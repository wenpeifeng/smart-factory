package com.erp.notify.core;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.notify.channel.ChannelCredentials;
import com.erp.notify.channel.NotifyChannel;
import com.erp.notify.channel.NotifyMessage;
import com.erp.notify.channel.NotifyResult;
import com.erp.notify.dto.BindingForm;
import com.erp.notify.dto.BindingQuery;
import com.erp.notify.dto.BindingVO;
import com.erp.notify.dto.ChannelForm;
import com.erp.notify.dto.ChannelQuery;
import com.erp.notify.dto.ChannelVO;
import com.erp.notify.dto.SendLogQuery;
import com.erp.notify.dto.SendLogVO;
import com.erp.notify.entity.NotifyChannelConfig;
import com.erp.notify.entity.NotifySendLog;
import com.erp.notify.entity.NotifyUserBinding;
import com.erp.notify.event.NotifyEvents;
import com.erp.notify.mapper.NotifyChannelConfigMapper;
import com.erp.notify.mapper.NotifyRefMapper;
import com.erp.notify.mapper.NotifySendLogMapper;
import com.erp.notify.mapper.NotifyUserBindingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 统一通知服务实现。
 * <p>
 * 事件监听：{@code @TransactionalEventListener(AFTER_COMMIT)} —— 业务事务提交后才外发，
 * 回滚的流程不产生"幽灵通知"；非事务发布（预警定时任务）经 fallbackExecution 立即执行。
 * <p>
 * 降级语义（与 workflow 侧站内信互补，不重复兜底）：
 * <ul>
 *   <li>无启用通道：workflow 自身的 wf_message 已是站内信（本侧只记 debug 日志，不再补写）；</li>
 *   <li>通道启用但发送失败（重试 1 次后）或接收人未绑定外部账号：写发送日志 +
 *       log.warn + 补写一条【通道降级】wf_message（接收人仍可在消息中心收到提醒）。</li>
 * </ul>
 */
@Slf4j
@Service
public class NotifyServiceImpl implements NotifyService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 单据类型展示名（与前端 BIZ_TYPE_MAP / 字典 wf_biz_type 对齐） */
    private static final Map<String, String> BIZ_TYPE_NAMES = Map.of(
            "pur_order", "采购订单", "sal_order", "销售订单",
            "pur_instock", "采购入库单", "sal_outstock", "销售出库单",
            "mrp_work_order", "生产订单", "fin_payment", "付款单", "fin_receipt", "收款单");

    /** 预警类型展示名 */
    private static final Map<String, String> WARN_TYPE_NAMES = Map.of(
            "stock", "库存安全线", "credit", "客户信用", "order", "生产超期");

    private final NotifyChannelConfigMapper configMapper;
    private final NotifySendLogMapper sendLogMapper;
    private final NotifyUserBindingMapper bindingMapper;
    private final NotifyRefMapper refMapper;
    private final NotifyChannelCache channelCache;
    /** 全部通道实现（Spring 注入，按 type 路由） */
    private final Map<String, NotifyChannel> channels;

    public NotifyServiceImpl(NotifyChannelConfigMapper configMapper,
                             NotifySendLogMapper sendLogMapper,
                             NotifyUserBindingMapper bindingMapper,
                             NotifyRefMapper refMapper,
                             NotifyChannelCache channelCache,
                             List<NotifyChannel> channelBeans) {
        this.configMapper = configMapper;
        this.sendLogMapper = sendLogMapper;
        this.bindingMapper = bindingMapper;
        this.refMapper = refMapper;
        this.channelCache = channelCache;
        this.channels = new LinkedHashMap<>();
        for (NotifyChannel channel : channelBeans) {
            this.channels.put(channel.type(), channel);
        }
    }

    /* ===================== 事件监听（workflow → notify） ===================== */

    /** 审批待办：任务生成点发布 → 启用通道推送（单据类型+单号+金额+发起人+审批链接） */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTaskCreated(NotifyEvents.TaskCreatedEvent event) {
        String bizTypeName = BIZ_TYPE_NAMES.getOrDefault(event.bizType(), event.bizType());
        BigDecimal amount = event.amount();
        StringBuilder markdown = new StringBuilder("### 【ERP 审批待办】\n")
                .append("**单据类型**：").append(bizTypeName).append('\n')
                .append("**单号**：").append(event.bizNo()).append('\n');
        if (amount != null) {
            markdown.append("**金额**：").append(amount.stripTrailingZeros().toPlainString()).append('\n');
        }
        markdown.append("**发起人**：").append(event.startUserName() == null ? event.startUserId() : event.startUserName()).append('\n')
                .append("**当前节点**：").append(event.nodeName());
        if (event.flowName() != null && !event.flowName().isBlank()) {
            markdown.append("（流程：").append(event.flowName()).append("）");
        }
        String title = "审批待办：" + event.bizNo();
        dispatch(NotifyChannelConfig.MASK_TODO, NotifySendLog.EVENT_TODO, event.taskId(),
                event.assigneeId(), title, markdown.toString(),
                WfMessageType.TODO, "单据[" + event.bizNo() + "]待您审批（外部通道推送失败降级）", "/workflow/todo");
    }

    /** 审批结果：实例通过/驳回发布 → 通知发起人 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApprovalResult(NotifyEvents.ApprovalResultEvent event) {
        String bizTypeName = BIZ_TYPE_NAMES.getOrDefault(event.bizType(), event.bizType());
        StringBuilder markdown = new StringBuilder("### 【ERP 审批结果】\n")
                .append("**单据类型**：").append(bizTypeName).append('\n')
                .append("**单号**：").append(event.bizNo()).append('\n')
                .append("**结果**：").append(event.approved() ? "✅ 已通过" : "❌ 已驳回").append('\n')
                .append("**审批节点**：").append(event.nodeName() == null ? "-" : event.nodeName()).append('\n')
                .append("**审批人**：").append(event.operatorName() == null ? "-" : event.operatorName());
        if (event.opinion() != null && !event.opinion().isBlank()) {
            markdown.append('\n').append("**意见**：").append(event.opinion());
        }
        String title = (event.approved() ? "审批通过：" : "审批驳回：") + event.bizNo();
        dispatch(NotifyChannelConfig.MASK_APPROVAL, NotifySendLog.EVENT_APPROVAL, null,
                event.startUserId(), title, markdown.toString(),
                event.approved() ? WfMessageType.APPROVE : WfMessageType.REJECT,
                "单据[" + event.bizNo() + "]审批" + (event.approved() ? "通过" : "被驳回") + "（外部通道推送失败降级）", "/workflow/todo");
    }

    /** 预警：扫描命中发布 → 群机器人 Markdown 卡片 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWarn(NotifyEvents.WarnEvent event) {
        String warnTypeName = WARN_TYPE_NAMES.getOrDefault(event.warnType(), event.warnType());
        String markdown = "### 【ERP 预警】" + event.title() + "\n"
                + "**预警类型**：" + warnTypeName + "\n"
                + "**内容**：" + event.content() + "\n"
                + "**时间**：" + LocalDateTime.now().format(TIME_FORMAT);
        dispatch(NotifyChannelConfig.MASK_WARN, NotifySendLog.EVENT_WARN, null,
                event.receiverUserId(), event.title(), markdown,
                WfMessageType.WARN, event.title() + "（外部通道推送失败降级）", event.bizPath());
    }

    /* ===================== 分发内核 ===================== */

    /** wf_message 消息类型常量（与 workflow 模块约定一致，避免跨模块 import） */
    private static final class WfMessageType {
        static final String TODO = "TODO";
        static final String APPROVE = "APPROVE";
        static final String REJECT = "REJECT";
        static final String WARN = "WARN";
    }

    /**
     * 事件分发：路由到启用通道 → 重试 1 次 → 落发送日志 → 失败/未绑定降级站内信。
     *
     * @param mask           事件位（MASK_TODO/MASK_APPROVAL/MASK_WARN）
     * @param eventType      日志事件类型（TODO/APPROVAL/WARN）
     * @param taskId         审批任务 ID（待办事件携带，用于拼移动端审批链接；其余事件 null）
     * @param receiverUserId 接收人（webhook 群通道无接收人语义可为 null）
     * @param title          标题
     * @param markdown       正文
     * @param fallbackType   降级 wf_message 的消息类型
     * @param fallbackTitle  降级消息标题
     * @param fallbackPath   降级消息跳转路径
     */
    private void dispatch(int mask, String eventType, Long taskId, Long receiverUserId, String title, String markdown,
                          String fallbackType, String fallbackTitle, String fallbackPath) {
        try {
            List<NotifyChannelConfig> configs = channelCache.enabledFor(mask);
            if (configs.isEmpty()) {
                // 无启用通道：workflow 自身已写 wf_message 站内信，本侧无需重复兜底
                log.debug("[通知] 事件[{}]无启用通道，仅站内信", eventType);
                return;
            }
            boolean needFallback = false;
            for (NotifyChannelConfig config : configs) {
                NotifyChannel channel = channels.get(config.getChannelType());
                if (channel == null) {
                    log.warn("[通知] 通道类型[{}]无实现Bean，跳过", config.getChannelType());
                    continue;
                }
                String receiverIdentify = null;
                if (needsBinding(config.getChannelType())) {
                    NotifyUserBinding binding = findBinding(receiverUserId, config.getChannelType());
                    if (binding == null) {
                        log.warn("[通知] 用户[{}]未绑定{}外部账号，跳过通道[{}]并降级站内信",
                                receiverUserId, config.getChannelType(), config.getChannelName());
                        needFallback = true;
                        continue;
                    }
                    receiverIdentify = binding.getExternalId();
                }
                // 待办事件：按通道配置的移动端基地址拼审批链接（Markdown 通用语法，三家通道一致）
                String effectiveMarkdown = markdown;
                String link = approveLink(config, taskId);
                if (link != null) {
                    effectiveMarkdown = markdown + "\n\n[点击前往审批](" + link + ")";
                }
                NotifyMessage message = new NotifyMessage(eventType, receiverUserId, title,
                        effectiveMarkdown, link, receiverIdentify);
                NotifyResult result = sendWithRetry(channel, message, config);
                writeSendLog(config, eventType, receiverUserId, receiverIdentify, title, effectiveMarkdown, result);
                if (!result.success()) {
                    needFallback = true;
                }
            }
            if (needFallback) {
                fallbackToMessage(receiverUserId, fallbackType, fallbackTitle, fallbackPath);
            }
        } catch (Exception e) {
            // 通知是旁路能力：任何异常不允许影响主流程（此处已是事务外，仅记录）
            log.error("[通知] 事件[{}]分发异常: {}", eventType, e.getMessage(), e);
        }
    }

    /** 发送 + 失败重试 1 次 */
    private NotifyResult sendWithRetry(NotifyChannel channel, NotifyMessage message, NotifyChannelConfig config) {
        NotifyResult result = channel.send(message, config);
        if (result.success()) {
            return result;
        }
        log.warn("[通知] 通道[{}]首次发送失败({})，重试 1 次", config.getChannelName(), result.error());
        NotifyResult retry = channel.send(message, config);
        return retry.success() ? retry
                : NotifyResult.fail("重试仍失败：" + result.error() + " / " + retry.error());
    }

    /** 落发送日志（旁路：日志失败只 warn） */
    private void writeSendLog(NotifyChannelConfig config, String eventType, Long receiverUserId,
                              String receiverIdentify, String title, String markdown, NotifyResult result) {
        try {
            NotifySendLog sendLog = new NotifySendLog();
            sendLog.setChannelId(config.getId());
            sendLog.setChannelType(config.getChannelType());
            sendLog.setEventType(eventType);
            sendLog.setReceiverId(receiverUserId);
            sendLog.setReceiver(receiverIdentify != null ? receiverIdentify : webhookHost(config));
            sendLog.setTitle(title);
            sendLog.setContent(markdown);
            sendLog.setSuccess(result.success() ? 1 : 0);
            sendLog.setErrorMsg(truncate(result.error(), 500));
            sendLogMapper.insert(sendLog);
        } catch (Exception e) {
            log.warn("[通知] 发送日志落库失败: {}", e.getMessage());
        }
    }

    /** 降级站内信：补写一条 wf_message（仅个人接收人场景；群通道失败无个人降级对象） */
    private void fallbackToMessage(Long receiverUserId, String msgType, String title, String bizPath) {
        if (receiverUserId == null) {
            log.warn("[通知] 群通道推送失败且无个人接收人，不降级站内信");
            return;
        }
        try {
            refMapper.insertFallbackMessage(receiverUserId, msgType,
                    "【通道降级】" + title,
                    "外部通知通道推送失败，已降级站内信：" + title,
                    bizPath, 1000L);
            log.warn("[通知] 已降级站内信：用户[{}] 类型[{}] 标题[{}]", receiverUserId, msgType, title);
        } catch (Exception e) {
            log.warn("[通知] 站内信降级落库失败: {}", e.getMessage());
        }
    }

    /** 该通道类型是否需要用户绑定（钉钉/企微点对点；webhook 群广播不需要） */
    private boolean needsBinding(String channelType) {
        return NotifyChannelConfig.TYPE_DINGTALK.equals(channelType)
                || NotifyChannelConfig.TYPE_WECOM.equals(channelType);
    }

    /** 查用户在某通道的外部账号绑定 */
    private NotifyUserBinding findBinding(Long userId, String channelType) {
        if (userId == null) {
            return null;
        }
        return bindingMapper.selectOne(Wrappers.lambdaQuery(NotifyUserBinding.class)
                .eq(NotifyUserBinding::getUserId, userId)
                .eq(NotifyUserBinding::getChannel, channelType)
                .last("LIMIT 1"));
    }

    /** 审批链接 = 通道配置的移动端基地址 + /m/approve/{taskId}（未配置基地址/无 taskId 返回 null） */
    private String approveLink(NotifyChannelConfig config, Long taskId) {
        if (taskId == null || config.getMobileBaseUrl() == null || config.getMobileBaseUrl().isBlank()) {
            return null;
        }
        String base = config.getMobileBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/m/approve/" + taskId;
    }

    /** webhook 接收人展示：URL host */
    private String webhookHost(NotifyChannelConfig config) {
        String url = ChannelCredentials.parse(config.getCredentials()).get("webhookUrl");
        if (url == null) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return truncate(url, 100);
        }
    }

    /* ===================== 通道配置管理 ===================== */

    @Override
    public PageResult<ChannelVO> pageChannels(ChannelQuery query) {
        return PageUtils.of(configMapper, Wrappers.lambdaQuery(NotifyChannelConfig.class)
                        .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                                NotifyChannelConfig::getChannelName, query.getKeyword())
                        .eq(query.getChannelType() != null && !query.getChannelType().isBlank(),
                                NotifyChannelConfig::getChannelType, query.getChannelType())
                        .eq(query.getStatus() != null, NotifyChannelConfig::getStatus, query.getStatus())
                        .orderByDesc(NotifyChannelConfig::getId),
                query.getPageNum(), query.getPageSize(), this::toVo);
    }

    @Override
    public Long createChannel(ChannelForm form) {
        NotifyChannelConfig entity = new NotifyChannelConfig();
        applyForm(entity, form, null);
        configMapper.insert(entity);
        channelCache.evict();
        return entity.getId();
    }

    @Override
    public void updateChannel(Long id, ChannelForm form) {
        NotifyChannelConfig entity = requireChannel(id);
        applyForm(entity, form, entity.getCredentials());
        configMapper.updateById(entity);
        channelCache.evict();
    }

    @Override
    public void deleteChannel(Long id) {
        requireChannel(id);
        configMapper.deleteById(id);
        channelCache.evict();
    }

    @Override
    public void changeChannelStatus(Long id, int status) {
        if (status != NotifyChannelConfig.STATUS_ENABLED && status != NotifyChannelConfig.STATUS_DISABLED) {
            throw BusinessException.ofKey("erp.workflow.illegal-status", status);
        }
        NotifyChannelConfig entity = requireChannel(id);
        entity.setStatus(status);
        configMapper.updateById(entity);
        channelCache.evict();
    }

    @Override
    public SendLogVO testSend(Long id, Long receiverUserId) {
        NotifyChannelConfig config = requireChannel(id);
        NotifyChannel channel = channels.get(config.getChannelType());
        if (channel == null) {
            throw BusinessException.ofKey("erp.notify.channel-no-impl", config.getChannelType());
        }
        String receiverIdentify = null;
        if (needsBinding(config.getChannelType())) {
            if (receiverUserId == null) {
                throw BusinessException.ofKey("erp.notify.test-send-need-user");
            }
            NotifyUserBinding binding = findBinding(receiverUserId, config.getChannelType());
            if (binding == null) {
                throw BusinessException.ofKey("erp.notify.user-not-bound", receiverUserId, config.getChannelType());
            }
            receiverIdentify = binding.getExternalId();
        }
        String title = "通知通道测试：" + config.getChannelName();
        String markdown = "### 【ERP 通知通道测试】\n"
                + "**通道**：" + config.getChannelName() + "（" + config.getChannelType() + "）\n"
                + "**说明**：本消息由管理门户\"测试发送\"按钮触发\n"
                + "**时间**：" + LocalDateTime.now().format(TIME_FORMAT);
        NotifyMessage message = new NotifyMessage(NotifySendLog.EVENT_TEST, receiverUserId, title, markdown, null, receiverIdentify);

        long start = System.currentTimeMillis();
        NotifyResult result = sendWithRetry(channel, message, config);
        long cost = System.currentTimeMillis() - start;

        NotifySendLog sendLog = new NotifySendLog();
        sendLog.setChannelId(config.getId());
        sendLog.setChannelType(config.getChannelType());
        sendLog.setEventType(NotifySendLog.EVENT_TEST);
        sendLog.setReceiverId(receiverUserId);
        sendLog.setReceiver(receiverIdentify != null ? receiverIdentify : webhookHost(config));
        sendLog.setTitle(title);
        sendLog.setContent(markdown);
        sendLog.setSuccess(result.success() ? 1 : 0);
        sendLog.setErrorMsg(truncate(result.error(), 500));
        sendLog.setCostMs(cost);
        sendLogMapper.insert(sendLog);
        if (!result.success()) {
            throw BusinessException.ofKey("erp.notify.test-send-failed", result.error());
        }
        return SendLogVO.from(sendLog);
    }

    /* ===================== 发送日志 ===================== */

    @Override
    public PageResult<SendLogVO> pageSendLogs(SendLogQuery query) {
        return PageUtils.of(sendLogMapper, Wrappers.lambdaQuery(NotifySendLog.class)
                        .eq(query.getChannelId() != null, NotifySendLog::getChannelId, query.getChannelId())
                        .eq(query.getChannelType() != null && !query.getChannelType().isBlank(),
                                NotifySendLog::getChannelType, query.getChannelType())
                        .eq(query.getEventType() != null && !query.getEventType().isBlank(),
                                NotifySendLog::getEventType, query.getEventType())
                        .eq(query.getSuccess() != null, NotifySendLog::getSuccess, query.getSuccess())
                        .orderByDesc(NotifySendLog::getId),
                query.getPageNum(), query.getPageSize(), SendLogVO::from);
    }

    /* ===================== 用户绑定 ===================== */

    @Override
    public PageResult<BindingVO> pageBindings(BindingQuery query) {
        PageResult<BindingVO> page = PageUtils.of(bindingMapper, Wrappers.lambdaQuery(NotifyUserBinding.class)
                        .eq(query.getUserId() != null, NotifyUserBinding::getUserId, query.getUserId())
                        .eq(query.getChannel() != null && !query.getChannel().isBlank(),
                                NotifyUserBinding::getChannel, query.getChannel())
                        .like(query.getExternalId() != null && !query.getExternalId().isBlank(),
                                NotifyUserBinding::getExternalId, query.getExternalId())
                        .orderByDesc(NotifyUserBinding::getId),
                query.getPageNum(), query.getPageSize(), BindingVO::from);
        // 补充用户昵称（页量级小，逐行查可接受）
        Map<Long, String> nicknameCache = new java.util.HashMap<>();
        for (BindingVO vo : page.getRecords()) {
            vo.setUserName(nicknameCache.computeIfAbsent(vo.getUserId(), refMapper::selectUserNickname));
        }
        return page;
    }

    @Override
    public Long createBinding(BindingForm form) {
        // 同用户同通道唯一：已有则更新 external_id（uk_user_channel 兜底并发）
        NotifyUserBinding existing = findBinding(form.getUserId(), form.getChannel());
        if (existing != null) {
            existing.setExternalId(form.getExternalId());
            bindingMapper.updateById(existing);
            return existing.getId();
        }
        NotifyUserBinding entity = new NotifyUserBinding();
        entity.setUserId(form.getUserId());
        entity.setChannel(form.getChannel());
        entity.setExternalId(form.getExternalId());
        bindingMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void deleteBinding(Long id) {
        bindingMapper.deleteById(id);
    }

    /* ===================== 私有支撑 ===================== */

    /** 表单 → 实体（凭据按类型组装；oldCredentials 供修改时"空/掩码=保持原值"合并） */
    private void applyForm(NotifyChannelConfig entity, ChannelForm form, String oldCredentials) {
        String type = form.getChannelType();
        if (!NotifyChannelConfig.TYPE_WEBHOOK.equals(type)
                && !NotifyChannelConfig.TYPE_DINGTALK.equals(type)
                && !NotifyChannelConfig.TYPE_WECOM.equals(type)) {
            throw BusinessException.ofKey("erp.notify.channel-type-unsupported", type);
        }
        entity.setChannelType(type);
        entity.setChannelName(form.getChannelName());
        entity.setEventMask(form.getEventMask() == null ? NotifyChannelConfig.MASK_ALL : form.getEventMask());
        entity.setMobileBaseUrl(blankToNull(form.getMobileBaseUrl()));
        entity.setStatus(form.getStatus() == null ? NotifyChannelConfig.STATUS_ENABLED : form.getStatus());
        entity.setRemark(blankToNull(form.getRemark()));
        entity.setCredentials(buildCredentials(type, form, ChannelCredentials.parse(oldCredentials)));
    }

    /** 按通道类型组装凭据 JSON；必填缺失抛错；空值/掩码串(****开头)沿用旧值 */
    private String buildCredentials(String type, ChannelForm form, ChannelCredentials old) {
        Map<String, String> credentials = new TreeMap<>();
        switch (type) {
            case NotifyChannelConfig.TYPE_WEBHOOK -> {
                String url = mergeCredential(form.getWebhookUrl(), old.get("webhookUrl"));
                if (url == null) {
                    throw BusinessException.ofKey("erp.notify.webhook-url-required");
                }
                credentials.put("webhookUrl", url);
            }
            case NotifyChannelConfig.TYPE_DINGTALK -> {
                putRequired(credentials, "corpId", mergeCredential(form.getCorpId(), old.get("corpId")), "corpId");
                putRequired(credentials, "agentId", mergeCredential(form.getAgentId(), old.get("agentId")), "agentId");
                putRequired(credentials, "appSecret", mergeCredential(form.getAppSecret(), old.get("appSecret")), "appSecret");
                putOptional(credentials, "aesKey", mergeCredential(form.getAesKey(), old.get("aesKey")));
                putOptional(credentials, "token", mergeCredential(form.getToken(), old.get("token")));
            }
            case NotifyChannelConfig.TYPE_WECOM -> {
                putRequired(credentials, "corpId", mergeCredential(form.getCorpId(), old.get("corpId")), "corpId");
                putRequired(credentials, "agentId", mergeCredential(form.getAgentId(), old.get("agentId")), "agentId");
                putRequired(credentials, "secret", mergeCredential(form.getSecret(), old.get("secret")), "secret");
                putOptional(credentials, "encodingAesKey", mergeCredential(form.getAesKey(), old.get("encodingAesKey")));
                putOptional(credentials, "token", mergeCredential(form.getToken(), old.get("token")));
            }
            default -> throw BusinessException.ofKey("erp.notify.channel-type-unsupported2", type);
        }
        try {
            return MAPPER.writeValueAsString(credentials);
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.notify.credential-serialize-failed", e.getMessage());
        }
    }

    /** 表单值与旧值合并：空/掩码串表示"保持原值" */
    private String mergeCredential(String incoming, String old) {
        if (incoming == null || incoming.isBlank() || incoming.startsWith("****")) {
            return old;
        }
        return incoming.trim();
    }

    private void putRequired(Map<String, String> target, String key, String value, String label) {
        if (value == null) {
            throw BusinessException.ofKey("erp.notify.channel-config-required", label);
        }
        target.put(key, value);
    }

    private void putOptional(Map<String, String> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /** 实体转 VO：凭据脱敏（保留末 4 位；webhookUrl 保留 host） */
    private ChannelVO toVo(NotifyChannelConfig entity) {
        ChannelVO vo = ChannelVO.from(entity);
        ChannelCredentials credentials = ChannelCredentials.parse(entity.getCredentials());
        Map<String, String> masked = new TreeMap<>();
        List<String> keys = new ArrayList<>();
        for (String key : List.of("webhookUrl", "corpId", "agentId", "appSecret", "secret", "token", "aesKey", "encodingAesKey")) {
            String value = credentials.get(key);
            if (value == null) {
                continue;
            }
            keys.add(key);
            masked.put(key, maskValue(key, value));
        }
        vo.setCredentials(masked);
        vo.setCredentialKeys(keys);
        return vo;
    }

    /** 脱敏：secret 类仅露末 4 位；corpId/agentId/token 全掩码；webhookUrl 保 host+路径掩码 */
    private String maskValue(String key, String value) {
        if ("webhookUrl".equals(key)) {
            try {
                URI uri = URI.create(value);
                return (uri.getScheme() != null ? uri.getScheme() + "://" : "") + uri.getHost() + "/****";
            } catch (Exception e) {
                return "****";
            }
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private NotifyChannelConfig requireChannel(Long id) {
        NotifyChannelConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey("erp.notify.channel-not-exists", id);
        }
        return entity;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}

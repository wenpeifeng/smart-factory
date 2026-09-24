package com.erp.notify.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.notify.channel.ChannelCredentials;
import com.erp.notify.core.ApprovalCallbackHandler;
import com.erp.notify.entity.NotifyChannelConfig;
import com.erp.notify.mapper.NotifyChannelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 开放平台审批回调接口（安全白名单放行，凭签名/加密保护）。
 * <p>
 * 钉钉：POST /notify/callback/dingtalk?signature=&timestamp=&nonce= body={"encrypt":"..."}，
 *       密钥取 dingtalk 通道凭据 aesKey（可选配置）；
 * 企微：GET（URL 有效性验证，回显解密后的 echostr）+ POST（事件推送 XML），
 *       密钥取 wecom 通道凭据 encodingAesKey + token。
 * <p>
 * 审批动作路由：解密报文中的 taskId + action（approve/reject）经
 * {@link ApprovalCallbackHandler}（erp-workflow 装配）回写审批。
 * <b>凭据未配置时的直审方案</b>：推送链接指向 H5 移动审批页
 * /m/approve/{taskId}（登录后直接通过/驳回，走既有 workflow 接口），无需回调。
 */
@Slf4j
@RestController
@RequestMapping("/notify/callback")
@RequiredArgsConstructor
public class NotifyCallbackController {

    private final NotifyChannelConfigMapper configMapper;
    private final ObjectProvider<ApprovalCallbackHandler> callbackHandler;

    /* ===================== 企业微信 ===================== */

    /**
     * 企微回调 URL 有效性验证（配置回调时企微服务器 GET 本接口）。
     * 验签通过后回显解密的 echostr 明文。
     */
    @GetMapping("/wecom")
    public String wecomVerify(@RequestParam(required = false) String msg_signature,
                              @RequestParam(required = false) String timestamp,
                              @RequestParam(required = false) String nonce,
                              @RequestParam(required = false) String echostr) {
        CredentialsWithCorp credentials = findCredentials(NotifyChannelConfig.TYPE_WECOM, "encodingAesKey");
        if (credentials == null || !credentials.aesKeyConfigured()) {
            return "wecom channel not configured(token/encodingAesKey)";
        }
        if (!CallbackCrypto.verifySignature(credentials.token(), timestamp, nonce, echostr, msg_signature)) {
            log.warn("[通知] 企微回调验签失败");
            return "signature mismatch";
        }
        String plain = CallbackCrypto.decrypt(credentials.aesKey(), echostr);
        return plain != null ? plain : "decrypt failed";
    }

    /**
     * 企微事件推送（加密 XML）。当前支持审批动作报文（预留），
     * 其余事件返回 success 静默确认，避免企微重试轰炸。
     */
    @PostMapping("/wecom")
    public String wecomEvent(@RequestParam(required = false) String msg_signature,
                             @RequestParam(required = false) String timestamp,
                             @RequestParam(required = false) String nonce,
                             @RequestBody String body) {
        CredentialsWithCorp credentials = findCredentials(NotifyChannelConfig.TYPE_WECOM, "encodingAesKey");
        if (credentials == null || !credentials.aesKeyConfigured()) {
            return "success";
        }
        String encrypt = extractEncrypt(body);
        if (encrypt == null || !CallbackCrypto.verifySignature(credentials.token(), timestamp, nonce, encrypt, msg_signature)) {
            log.warn("[通知] 企微事件验签失败");
            return "success";
        }
        String plain = CallbackCrypto.decrypt(credentials.aesKey(), encrypt);
        if (plain != null) {
            routeApproval("wecom", plain);
        }
        return "success";
    }

    /* ===================== 钉钉 ===================== */

    /**
     * 钉钉事件订阅回调（加密 JSON）。
     * 验签 + 解密后路由审批动作；响应需按钉钉协议回加密 JSON。
     */
    @PostMapping("/dingtalk")
    public Map<String, String> dingtalkEvent(@RequestParam(value = "sign", required = false) String sign,
                                             @RequestParam(required = false) String timestamp,
                                             @RequestParam(required = false) String nonce,
                                             @RequestBody String body) {
        CredentialsWithCorp credentials = findCredentials(NotifyChannelConfig.TYPE_DINGTALK, "aesKey");
        if (credentials == null || !credentials.aesKeyConfigured()) {
            return Map.of("msg", "dingtalk channel not configured(aesKey)");
        }
        String encrypt = extractEncrypt(body);
        if (encrypt == null
                || !CallbackCrypto.verifySignature(credentials.token(), timestamp, nonce, encrypt, sign)) {
            log.warn("[通知] 钉钉回调验签失败");
            return Map.of("msg", "signature mismatch");
        }
        String plain = CallbackCrypto.decrypt(credentials.aesKey(), encrypt);
        String result = plain != null
                ? routeApproval("dingtalk", plain)
                : "decrypt failed";

        // 钉钉协议：响应体回加密 JSON {"msg_signature","timeStamp","nonce","encrypt"}
        String replyEncrypt = CallbackCrypto.encrypt(credentials.aesKey(), "{\"msg\":\"" + result + "\"}", credentials.corpId());
        if (replyEncrypt == null) {
            return Map.of("msg", result);
        }
        String replySignature = CallbackCrypto.signature(credentials.token(), timestamp, nonce, replyEncrypt);
        return Map.of(
                "msg_signature", replySignature == null ? "" : replySignature,
                "timeStamp", timestamp == null ? String.valueOf(System.currentTimeMillis() / 1000) : timestamp,
                "nonce", nonce == null ? "" : nonce,
                "encrypt", replyEncrypt);
    }

    /* ===================== 私有支撑 ===================== */

    /** 钉钉/企微报文形态统一抽取 encrypt 字段（JSON 的 encrypt 键 / XML 的 <Encrypt> 节点） */
    private String extractEncrypt(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            // JSON：{"encrypt":"..."}
            int key = trimmed.indexOf("\"encrypt\"");
            if (key < 0) {
                return null;
            }
            int colon = trimmed.indexOf(':', key);
            int start = trimmed.indexOf('"', colon + 1);
            int end = trimmed.indexOf('"', start + 1);
            return start > 0 && end > start ? trimmed.substring(start + 1, end) : null;
        }
        // XML：<Encrypt>...</Encrypt>
        int start = trimmed.indexOf("<Encrypt>");
        int end = trimmed.indexOf("</Encrypt>");
        return start >= 0 && end > start ? trimmed.substring(start + 9, end) : null;
    }

    /**
     * 从解密报文路由审批动作。识别两种载体：
     * JSON（自定义/钉钉 bpms 事件）取 taskId/action/opinion；
     * XML（企微应用消息）取同名节点。不识别的报文静默忽略。
     */
    private String routeApproval(String source, String plain) {
        Long taskId = extractField(plain, "taskId");
        if (taskId == null) {
            taskId = extractField(plain, "task_id");
        }
        String action = extractTextField(plain, "action");
        if (action == null) {
            action = extractTextField(plain, "result");
        }
        if (taskId == null || action == null) {
            log.debug("[通知] {} 回调非审批动作报文，忽略: {}", source, truncate(plain));
            return "ignored";
        }
        boolean approve = "approve".equalsIgnoreCase(action)
                || "agree".equalsIgnoreCase(action)
                || "1".equals(action);
        String opinion = extractTextField(plain, "opinion");
        ApprovalCallbackHandler handler = callbackHandler.getIfAvailable();
        if (handler == null) {
            log.warn("[通知] 审批回调处理器未装配(workflow 模块)，动作被忽略");
            return "no approval handler";
        }
        try {
            return handler.handleApproval(source, taskId, approve, opinion);
        } catch (Exception e) {
            log.warn("[通知] 审批回调处理失败 taskId={}: {}", taskId, e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /** 从 JSON/XML 报文提取数值字段 */
    private Long extractField(String plain, String field) {
        String value = extractTextField(plain, field);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 JSON/XML 报文提取文本字段（JSON "field":"v" / XML <field>v</field>） */
    private String extractTextField(String plain, String field) {
        String trimmed = plain.trim();
        if (trimmed.startsWith("{")) {
            int key = trimmed.indexOf("\"" + field + "\"");
            if (key < 0) {
                return null;
            }
            int colon = trimmed.indexOf(':', key);
            int start = trimmed.indexOf('"', colon + 1);
            int end = trimmed.indexOf('"', start + 1);
            return start > 0 && end > start ? trimmed.substring(start + 1, end) : null;
        }
        int start = trimmed.indexOf("<" + field + ">");
        int end = trimmed.indexOf("</" + field + ">");
        return start >= 0 && end > start ? trimmed.substring(start + field.length() + 2, end) : null;
    }

    private String truncate(String text) {
        return text != null && text.length() > 200 ? text.substring(0, 200) : text;
    }

    /** 查指定类型通道的回调凭据（token + aesKey + corpId；取第一条配置的） */
    private CredentialsWithCorp findCredentials(String channelType, String aesKeyName) {
        List<NotifyChannelConfig> configs = configMapper.selectList(Wrappers.lambdaQuery(NotifyChannelConfig.class)
                .eq(NotifyChannelConfig::getChannelType, channelType)
                .last("LIMIT 1"));
        if (configs.isEmpty()) {
            return null;
        }
        ChannelCredentials credentials = ChannelCredentials.parse(configs.get(0).getCredentials());
        return new CredentialsWithCorp(
                credentials.get("token"),
                credentials.get(aesKeyName),
                credentials.get("corpId"));
    }

    /**
     * 回调凭据快照。
     *
     * @param token 回调验签 token（null=未配置）
     * @param aesKey 加密密钥（null=未配置）
     * @param corpId 企业 ID（钉钉响应加密需要）
     */
    private record CredentialsWithCorp(String token, String aesKey, String corpId) {
        boolean aesKeyConfigured() {
            return token != null && aesKey != null;
        }
    }
}

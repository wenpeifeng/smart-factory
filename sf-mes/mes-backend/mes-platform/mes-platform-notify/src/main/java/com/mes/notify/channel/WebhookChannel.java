package com.mes.notify.channel;

import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.notify.entity.NotifyChannelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 群机器人通道（webhook）—— 钉钉自定义机器人 / 企微群机器人通用。
 * <p>
 * 两家群机器人的 Markdown 报文仅 markdown 内层键名不同（钉钉 text、企微 content），
 * 互相忽略对方键，因此发一份三键合体 JSON 即可通用：
 * <pre>{@code
 * {"msgtype":"markdown","markdown":{"title":"...","text":"...","content":"..."}}
 * }</pre>
 * 预警群通知与待办推送默认走本通道，无需开放平台凭据，配置 webhook 地址即可真实发送。
 */
@Slf4j
@Component
public class WebhookChannel implements NotifyChannel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** HTTP 超时（毫秒） */
    private static final int TIMEOUT_MS = 5000;

    @Override
    public String type() {
        return NotifyChannelConfig.TYPE_WEBHOOK;
    }

    @Override
    public NotifyResult send(NotifyMessage message, NotifyChannelConfig config) {
        ChannelCredentials credentials = ChannelCredentials.parse(config.getCredentials());
        String webhookUrl = credentials.get("webhookUrl");
        if (webhookUrl == null) {
            return NotifyResult.fail("webhook 通道未配置 webhookUrl");
        }
        try {
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", message.getTitle());
            markdown.put("text", message.getMarkdown());
            markdown.put("content", message.getMarkdown());
            Map<String, Object> body = Map.of("msgtype", "markdown", "markdown", markdown);

            String response = HttpUtil.post(webhookUrl, MAPPER.writeValueAsString(body), TIMEOUT_MS);
            return parseResult(response);
        } catch (Exception e) {
            log.warn("[通知] webhook 发送异常 url={} title={}: {}", maskUrl(webhookUrl), message.getTitle(), e.getMessage());
            return NotifyResult.fail(e.getMessage());
        }
    }

    /** 平台返回解析：HTTP 200 且 errcode=0（或无 errcode 的第三方测试服务）视为成功 */
    private NotifyResult parseResult(String response) {
        if (response == null || response.isBlank()) {
            return NotifyResult.ok();
        }
        try {
            JsonNode node = MAPPER.readTree(response);
            if (node.hasNonNull("errcode") && node.get("errcode").asInt() != 0) {
                return NotifyResult.fail("errcode=" + node.get("errcode").asInt()
                        + " errmsg=" + node.path("errmsg").asText());
            }
            return NotifyResult.ok();
        } catch (Exception e) {
            // 非 JSON 响应（部分公网 webhook 测试服务返回纯文本），HTTP 层已成功即视为送达
            return NotifyResult.ok();
        }
    }

    /** 日志脱敏：URL 只保留 host */
    private String maskUrl(String url) {
        int scheme = url.indexOf("://");
        int start = scheme < 0 ? 0 : scheme + 3;
        int slash = url.indexOf('/', start);
        return slash < 0 ? url : url.substring(0, slash) + "/***";
    }
}

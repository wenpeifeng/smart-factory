package com.erp.notify.channel;

import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.notify.entity.NotifyChannelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业微信自建应用通道（wecom）。
 * <p>
 * 凭据：{@code corpId + secret + agentId}（可选 token/encodingAesKey 供回调验签）。
 * 链路：gettoken 缓存 → message/send（Markdown，touser=notify_user_binding 的
 * external_id，即企微成员 userid，通常配置为员工手机号）。
 * 官方文档：https://developer.work.weixin.qq.com/document/path/90236
 */
@Slf4j
@Component
public class WecomChannel implements NotifyChannel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send";

    private static final int TIMEOUT_MS = 5000;
    /** 令牌提前刷新余量（毫秒） */
    private static final long TOKEN_REFRESH_AHEAD_MS = 60_000L;

    /** 令牌缓存：corpid:secret → [token, 过期时间戳] */
    private final Map<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    private record TokenEntry(String token, long expireAt) {
    }

    @Override
    public String type() {
        return NotifyChannelConfig.TYPE_WECOM;
    }

    @Override
    public NotifyResult send(NotifyMessage message, NotifyChannelConfig config) {
        ChannelCredentials credentials = ChannelCredentials.parse(config.getCredentials());
        if (!credentials.hasAll("corpId", "secret", "agentId")) {
            return NotifyResult.fail("企微通道凭据不完整(需 corpId/agentId/secret)");
        }
        String corpId = credentials.get("corpId");
        String secret = credentials.get("secret");
        String agentId = credentials.get("agentId");
        String touser = message.getReceiverIdentify();
        if (touser == null || touser.isBlank()) {
            return NotifyResult.fail("企微接收人(userid)未配置(notify_user_binding.external_id)");
        }

        try {
            String token = getToken(corpId, secret);
            if (token == null) {
                return NotifyResult.fail("获取企微 access_token 失败(请核对 corpId/secret)");
            }

            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("content", message.getMarkdown());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("touser", touser);
            body.put("msgtype", "markdown");
            body.put("agentid", Long.parseLong(agentId));
            body.put("markdown", markdown);

            String response = HttpUtil.post(SEND_URL + "?access_token=" + token,
                    MAPPER.writeValueAsString(body), TIMEOUT_MS);
            JsonNode node = MAPPER.readTree(response);
            if (node.path("errcode").asInt() != 0) {
                return NotifyResult.fail("errcode=" + node.path("errcode").asInt()
                        + " errmsg=" + node.path("errmsg").asText());
            }
            return NotifyResult.ok();
        } catch (NumberFormatException e) {
            return NotifyResult.fail("企微 agentId 必须为数字");
        } catch (Exception e) {
            log.warn("[通知] 企微发送异常 title={}: {}", message.getTitle(), e.getMessage());
            return NotifyResult.fail(e.getMessage());
        }
    }

    /** 取 access_token（缓存 + 提前刷新；失败返回 null 并清缓存） */
    private String getToken(String corpId, String secret) {
        String key = corpId + ":" + secret;
        TokenEntry entry = tokenCache.get(key);
        if (entry != null && entry.expireAt() > System.currentTimeMillis() + TOKEN_REFRESH_AHEAD_MS) {
            return entry.token();
        }
        try {
            String response = HttpUtil.get(TOKEN_URL
                    + "?corpid=" + corpId + "&corpsecret=" + secret, TIMEOUT_MS);
            JsonNode node = MAPPER.readTree(response);
            if (node.path("errcode").asInt() == 0 && node.hasNonNull("access_token")) {
                long expireAt = System.currentTimeMillis() + node.path("expires_in").asLong(7200) * 1000;
                tokenCache.put(key, new TokenEntry(node.get("access_token").asText(), expireAt));
                return node.get("access_token").asText();
            }
            log.warn("[通知] 企微 gettoken 失败: {}", response);
        } catch (Exception e) {
            log.warn("[通知] 企微 gettoken 异常: {}", e.getMessage());
        }
        tokenCache.remove(key);
        return null;
    }

}

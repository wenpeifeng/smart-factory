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
 * 钉钉企业内部应用通道（dingtalk）。
 * <p>
 * 凭据：{@code corpId + agentId + appSecret}（可选 aesKey/token 供回调验签）。
 * 链路：gettoken 缓存 →（接收人绑定手机号 getbymobile 换 userid，缓存）→
 * topapi/message/corp/asyncsend_v2 异步工作通知（Markdown）。
 * 官方文档：https://open.dingtalk.com/document/orgapp/obtain-the-access-token-of-an-enterprise-internal-app
 */
@Slf4j
@Component
public class DingTalkChannel implements NotifyChannel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TOKEN_URL = "https://oapi.dingtalk.com/gettoken";
    private static final String USER_BY_MOBILE_URL = "https://oapi.dingtalk.com/topapi/v2/user/getbymobile";
    private static final String ASYNC_SEND_URL = "https://oapi.dingtalk.com/topapi/message/corp/asyncsend_v2";

    private static final int TIMEOUT_MS = 5000;
    /** 令牌提前刷新余量（毫秒） */
    private static final long TOKEN_REFRESH_AHEAD_MS = 60_000L;

    /** 令牌缓存：corpid:secret → [token, 过期时间戳] */
    private final Map<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();
    /** 手机号 → userid 缓存（钉钉 userid 稳定，进程内缓存即可） */
    private final Map<String, String> useridCache = new ConcurrentHashMap<>();

    private record TokenEntry(String token, long expireAt) {
    }

    @Override
    public String type() {
        return NotifyChannelConfig.TYPE_DINGTALK;
    }

    @Override
    public NotifyResult send(NotifyMessage message, NotifyChannelConfig config) {
        ChannelCredentials credentials = ChannelCredentials.parse(config.getCredentials());
        if (!credentials.hasAll("corpId", "agentId", "appSecret")) {
            return NotifyResult.fail("钉钉通道凭据不完整(需 corpId/agentId/appSecret)");
        }
        String corpId = credentials.get("corpId");
        String agentId = credentials.get("agentId");
        String appSecret = credentials.get("appSecret");

        try {
            String token = getToken(corpId, appSecret);
            if (token == null) {
                return NotifyResult.fail("获取钉钉 access_token 失败(请核对 corpId/appSecret)");
            }
            String userid = resolveUserid(token, message.getReceiverIdentify());
            if (userid == null) {
                return NotifyResult.fail("钉钉手机号[" + mask(message.getReceiverIdentify()) + "]未匹配到企业成员userid");
            }

            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", message.getTitle());
            markdown.put("text", message.getMarkdown());
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("msgtype", "markdown");
            msg.put("markdown", markdown);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agent_id", agentId);
            body.put("userid_list", userid);
            body.put("msg", msg);

            String response = HttpUtil.post(ASYNC_SEND_URL + "?access_token=" + token,
                    MAPPER.writeValueAsString(body), TIMEOUT_MS);
            JsonNode node = MAPPER.readTree(response);
            if (node.path("errcode").asInt() != 0) {
                return NotifyResult.fail("errcode=" + node.path("errcode").asInt()
                        + " errmsg=" + node.path("errmsg").asText());
            }
            return NotifyResult.ok();
        } catch (Exception e) {
            log.warn("[通知] 钉钉发送异常 title={}: {}", message.getTitle(), e.getMessage());
            return NotifyResult.fail(e.getMessage());
        }
    }

    /** 取 access_token（缓存 + 提前刷新；失败返回 null 并清缓存） */
    private String getToken(String corpId, String appSecret) {
        String key = corpId + ":" + appSecret;
        TokenEntry entry = tokenCache.get(key);
        if (entry != null && entry.expireAt() > System.currentTimeMillis() + TOKEN_REFRESH_AHEAD_MS) {
            return entry.token();
        }
        try {
            String response = HttpUtil.get(TOKEN_URL
                    + "?corpid=" + corpId + "&corpsecret=" + appSecret, TIMEOUT_MS);
            JsonNode node = MAPPER.readTree(response);
            if (node.path("errcode").asInt() == 0 && node.hasNonNull("access_token")) {
                long expireAt = System.currentTimeMillis() + node.path("expires_in").asLong(7200) * 1000;
                tokenCache.put(key, new TokenEntry(node.get("access_token").asText(), expireAt));
                return node.get("access_token").asText();
            }
            log.warn("[通知] 钉钉 gettoken 失败: {}", response);
        } catch (Exception e) {
            log.warn("[通知] 钉钉 gettoken 异常: {}", e.getMessage());
        }
        tokenCache.remove(key);
        return null;
    }

    /** 手机号 → userid（getbymobile；缓存命中直接返回） */
    private String resolveUserid(String token, String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String cached = useridCache.get(mobile);
        if (cached != null) {
            return cached;
        }
        try {
            String response = HttpUtil.post(USER_BY_MOBILE_URL + "?access_token=" + token,
                    MAPPER.writeValueAsString(Map.of("mobile", mobile)), TIMEOUT_MS);
            JsonNode node = MAPPER.readTree(response);
            if (node.path("errcode").asInt() == 0) {
                String userid = node.path("result").path("userid").asText(null);
                if (userid != null && !userid.isBlank()) {
                    useridCache.put(mobile, userid);
                    return userid;
                }
            }
            log.warn("[通知] 钉钉 getbymobile 失败 mobile={}: {}", mask(mobile), response);
        } catch (Exception e) {
            log.warn("[通知] 钉钉 getbymobile 异常: {}", e.getMessage());
        }
        return null;
    }

    /** 手机号日志脱敏 */
    private String mask(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return "***";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}

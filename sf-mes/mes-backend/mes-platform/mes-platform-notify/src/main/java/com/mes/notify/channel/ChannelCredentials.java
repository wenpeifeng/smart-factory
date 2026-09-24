package com.mes.notify.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 凭据 JSON 访问器 —— 解析 notify_channel_config.credentials 为键值对。
 * <p>
 * 各通道按需取键：webhook 取 webhookUrl；dingtalk 取 corpId/agentId/appSecret/aesKey/token；
 * wecom 取 corpId/agentId/secret/token/encodingAesKey。坏 JSON 按空凭据处理
 * （发送时报"凭据缺失"失败，不抛异常阻断调度）。
 */
public final class ChannelCredentials {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, String> values;

    private ChannelCredentials(Map<String, String> values) {
        this.values = values == null ? Map.of() : values;
    }

    /** 解析凭据 JSON；坏 JSON/空串返回空凭据 */
    public static ChannelCredentials parse(String credentialsJson) {
        if (credentialsJson == null || credentialsJson.isBlank()) {
            return new ChannelCredentials(Map.of());
        }
        try {
            return new ChannelCredentials(MAPPER.readValue(credentialsJson, new TypeReference<>() {
            }));
        } catch (Exception e) {
            return new ChannelCredentials(Map.of());
        }
    }

    /** 按键取值；缺失/空白返回 null */
    public String get(String key) {
        String value = values.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 是否具备指定全部键 */
    public boolean hasAll(String... keys) {
        for (String key : keys) {
            if (get(key) == null) {
                return false;
            }
        }
        return true;
    }
}

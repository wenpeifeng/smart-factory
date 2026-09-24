package com.erp.notify.channel;

/**
 * 通道发送结果。
 *
 * @param success 是否成功
 * @param error   失败原因（成功为 null）
 */
public record NotifyResult(boolean success, String error) {

    public static NotifyResult ok() {
        return new NotifyResult(true, null);
    }

    public static NotifyResult fail(String error) {
        return new NotifyResult(false, error == null ? "unknown error" : error);
    }
}

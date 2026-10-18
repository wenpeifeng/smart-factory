package com.auth.idm.event;

import java.util.Set;

/**
 * 认证中心身份数据已变更，请在事务提交后推送子系统对账。
 *
 * @param clientIds      需要同步的应用；空集合表示所有已配置应用
 * @param originClientId 发起变更的子系统 client_id；认证中心管理端操作时为 null
 * @param reason         同步原因，仅用于日志与追踪
 */
public record IdentitySyncRequestedEvent(Set<String> clientIds, String originClientId, String reason) {

    public IdentitySyncRequestedEvent {
        clientIds = clientIds == null ? Set.of() : Set.copyOf(clientIds);
    }

    public static IdentitySyncRequestedEvent all(String originClientId, String reason) {
        return new IdentitySyncRequestedEvent(Set.of(), originClientId, reason);
    }

    public static IdentitySyncRequestedEvent client(String clientId, String originClientId, String reason) {
        return new IdentitySyncRequestedEvent(Set.of(clientId), originClientId, reason);
    }
}

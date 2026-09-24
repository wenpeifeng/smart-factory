package com.erp.notify.core;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.notify.entity.NotifyChannelConfig;
import com.erp.notify.mapper.NotifyChannelConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通道配置缓存 —— 事件分发高频读、管理门户低频写。
 * <p>
 * 进程内缓存"订阅指定事件位的启用通道列表"，TTL 60 秒兜底最终一致；
 * 通道 CRUD/启停后调用 {@link #evict()} 立即失效（本节点写本节点可见，
 * 多节点部署时其余节点最迟 60 秒收敛）。
 */
@Component
@RequiredArgsConstructor
public class NotifyChannelCache {

    /** 缓存 TTL（毫秒） */
    private static final long TTL_MS = 60_000L;

    private final NotifyChannelConfigMapper configMapper;

    /** 缓存：事件位 → [到期时间, 通道列表] */
    private final Map<Integer, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(long expireAt, List<NotifyChannelConfig> channels) {
    }

    /**
     * 查询订阅指定事件的全部启用通道。
     *
     * @param mask 事件位（NotifyChannelConfig.MASK_*）
     * @return 启用且订阅该事件的通道列表（可为空）
     */
    public List<NotifyChannelConfig> enabledFor(int mask) {
        CacheEntry entry = cache.get(mask);
        if (entry != null && entry.expireAt() > System.currentTimeMillis()) {
            return entry.channels();
        }
        List<NotifyChannelConfig> channels = configMapper.selectList(Wrappers.lambdaQuery(NotifyChannelConfig.class)
                .eq(NotifyChannelConfig::getStatus, NotifyChannelConfig.STATUS_ENABLED)
                .apply("(event_mask & {0}) <> 0", mask));
        cache.put(mask, new CacheEntry(System.currentTimeMillis() + TTL_MS, channels));
        return channels;
    }

    /** 失效全部缓存（通道配置变更后调用） */
    public void evict() {
        cache.clear();
    }
}

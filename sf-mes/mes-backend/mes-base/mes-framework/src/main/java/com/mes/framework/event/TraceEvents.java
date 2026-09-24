package com.mes.framework.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 追溯事件契约（上游模块发布，trace 模块监听落 trace_link）。
 * <p>放在 framework：事件是多模块共享契约，避免 trace 与 material/execution 相互依赖。
 * <p>用事件解耦依赖方向：material/execution 不依赖 trace（trace 依赖它们读取上下文）。
 */
public final class TraceEvents {

    private TraceEvents() {
    }

    /**
     * 领料审核事件：批次上料到工单（productNo=工单产出批次，取领料批次聚合）。
     */
    public record PickingAudited(
            /** 领料单 id */
            Long pickingId,
            /** 工单 id */
            Long workorderId,
            /** 行项目（批次+物料+数量） */
            List<Item> items,
            /** 审核时间 */
            LocalDateTime auditTime) {

        /** 上料行 */
        public record Item(
                /** 来料批次号（空串=不管理批次，跳过追溯） */
                String batchNo,
                /** 物料 id */
                Long materialId,
                /** 数量 */
                BigDecimal qty) {
        }
    }

    /**
     * 过站报工事件：关键件 SN 绑定产品 SN/工单。
     */
    public record StationReported(
            /** 过站记录 id */
            Long recordId,
            /** 工单 id */
            Long workorderId,
            /** 工序任务 id */
            Long woStepId,
            /** 产品 SN（SN 模式工单） */
            String sn,
            /** 关键件 SN 列表（已展开） */
            List<String> keyComponentSns,
            /** 过站时间 */
            LocalDateTime stationTime) {
    }
}

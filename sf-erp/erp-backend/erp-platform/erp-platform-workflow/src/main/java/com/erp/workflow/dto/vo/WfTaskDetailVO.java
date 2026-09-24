package com.erp.workflow.dto.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批任务详情 VO（移动端审批页 /m/approve/{taskId}）。
 * <p>
 * 在 WfTaskVO 基础上补充：单据金额（实例 vars.amount）与已见节点意见历史。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfTaskDetailVO extends WfTaskVO {

    /** 单据金额（发起时 vars.amount，可能为空） */
    private BigDecimal amount;

    /** 实例流程变量（JSON 原样，前端按需展示 amount 之外的自定义变量） */
    private String vars;

    /** 已处理节点的意见历史（按节点序号升序） */
    private List<OpinionItem> history;

    /** 单条意见历史 */
    @Data
    public static class OpinionItem {

        /** 节点名称 */
        private String nodeName;

        /** 节点序号 */
        private Integer nodeOrder;

        /** 审批人 ID */
        private Long approverId;

        /** 审批人姓名 */
        private String approverName;

        /** 任务状态：APPROVED/REJECTED/CANCELLED/PENDING */
        private String status;

        /** 审批意见 */
        private String opinion;

        /** 审批时间 */
        private LocalDateTime approveTime;
    }
}

package com.mes.workflow.entity;

import com.mes.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批流程定义头实体（wf_definition）。
 * <p>
 * 同一单据类型允许存在多条定义（历史停用版本），但只有 status=1（启用）的
 * 参与发起匹配（取 id 最大的一条，即最新启用的）；停用只影响新发起，
 * 在途实例继续按已生成的节点快照流转。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfDefinition extends BaseEntity {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    /** 流程编码（唯一，如 FLOW-PO-001） */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 适用单据类型：pur_order/sal_order/pur_instock/sal_outstock/mrp_work_order/fin_payment/fin_receipt */
    private String bizType;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}

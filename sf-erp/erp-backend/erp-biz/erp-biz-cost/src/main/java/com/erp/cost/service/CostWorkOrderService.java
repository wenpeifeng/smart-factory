package com.erp.cost.service;

import com.erp.cost.dto.form.OverheadForm;
import com.erp.cost.dto.request.WorkOrderQuery;
import com.erp.cost.dto.vo.WorkOrderCostDetailVO;
import com.erp.cost.dto.vo.WorkOrderCostRowVO;
import com.erp.common.result.PageResult;

/**
 * 工单成本归集服务：料（领料×移动加权价）工（计件工资约定表）费（手工分摊）三块归集。
 */
public interface CostWorkOrderService {

    /** 工单成本分页（每单归集料工费 + 单位成本） */
    PageResult<WorkOrderCostRowVO> page(WorkOrderQuery query, long pageNum, long pageSize);

    /**
     * 工单成本详情（GET /cost/work-orders/{id}）：
     * 领料明细行（×当时可得的移动加权价）、工资明细行（hr 约定表）、费用分摊、三费合计与单位成本。
     */
    WorkOrderCostDetailVO detail(Long workOrderId);

    /**
     * 制造费用分摊录入（POST /cost/work-orders/{id}/overhead）：整体覆盖式更新费/工时快照。
     */
    Long saveOverhead(Long workOrderId, OverheadForm form);
}

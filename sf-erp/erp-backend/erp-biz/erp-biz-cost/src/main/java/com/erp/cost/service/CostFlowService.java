package com.erp.cost.service;

import com.erp.cost.dto.form.HookReceiveForm;
import com.erp.cost.dto.request.CostFlowQuery;
import com.erp.cost.dto.vo.CostFlowVO;
import com.erp.cost.dto.vo.HookResultVO;
import com.erp.cost.dto.vo.SyncResultVO;
import com.erp.common.result.PageResult;

/**
 * 计价流水应用服务：流水查询 / 期间重算（sync）/ 单笔补录（hook）。
 */
public interface CostFlowService {

    /** 计价流水分页（筛选：期间/物料/业务类型/单号关键词） */
    PageResult<CostFlowVO> page(CostFlowQuery query, long pageNum, long pageSize);

    /**
     * 按库存流水重算指定期间的计价流水（初始化/补账）。
     * <p>
     * 重算窗口 [from, to]（yyyyMM，from 可空 = 从最早流水起）。
     * 已结账（CLOSED）期间受保护：窗口与其相交、或其后仍有已结账期间时拒绝执行；
     * 实际重算起点自动推到「最后一个已结账期间」的次月，运行余额从其台账续算。
     * PUR_IN 金额取采购入库单明细；无单价的入库（生产/其他入库、销售退货）
     * 按当时移动加权价估算 —— 与实时过账同一套计算路径。
     */
    SyncResultVO sync(String from, String to);

    /**
     * 手工补录一笔业务计价（POST /cost/hooks/receive）。
     * 入库方向（PUR_IN/OTH_IN/…）：amount 空则按当前加权价估算；
     * 出库方向（SAL_OUT/PROD_USE/…）：成本按当前移动加权价计算。
     */
    HookResultVO receive(HookReceiveForm form);
}

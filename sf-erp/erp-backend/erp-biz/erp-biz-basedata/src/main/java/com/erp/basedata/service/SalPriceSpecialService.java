package com.erp.basedata.service;

import com.erp.basedata.dto.form.PriceSpecialForm;
import com.erp.basedata.dto.vo.PriceSpecialVO;
import com.erp.basedata.entity.SalPriceSpecial;
import com.erp.common.result.PageResult;

/**
 * 特价审批 Service（申请 → workflow/管理员审批 → 生效）。
 * <p>
 * 申请后自动尝试挂 workflow（bizType=sal_price_special）：存在启用流程则生成待办
 * （结果由 {@code findEffective} 对 isApproved 惰性同步）；否则走管理员简单审批接口。
 */
public interface SalPriceSpecialService {

    /** workflow 业务类型（对应字典 wf_biz_type） */
    String WF_BIZ_TYPE = "sal_price_special";

    /**
     * 提交特价申请（status=0待审；返回后可能已被流程条件即时通过为 10）。
     *
     * @param form 申请表单
     * @return 申请 ID
     */
    Long apply(PriceSpecialForm form);

    /**
     * 特价申请分页。
     *
     * @param pageNum    页码
     * @param pageSize   页大小
     * @param customerId 客户（可空）
     * @param materialId 物料（可空）
     * @param status     状态（可空）
     * @return 分页结果（含客户/物料冗余名称）
     */
    PageResult<PriceSpecialVO> page(long pageNum, long pageSize, Long customerId, Long materialId, Integer status);

    /**
     * 管理员通过（status 0→10，条件 UPDATE 防重复审批）。
     *
     * @param id     申请 ID
     * @param remark 审批意见（可空）
     */
    void approve(Long id, String remark);

    /**
     * 管理员驳回（status 0→20，条件 UPDATE 防重复审批）。
     *
     * @param id     申请 ID
     * @param remark 审批意见（可空）
     */
    void reject(Long id, String remark);

    /**
     * 删除申请（仅待审可删，逻辑删除）。
     *
     * @param id 申请 ID
     */
    void delete(Long id);

    /**
     * 取客户+物料当前生效的特价（status=10 且在生效期内，多条取最新）；
     * 顺带把"workflow 已通过但状态仍 0"的记录惰性收敛为 10。
     * <p>
     * 定价引擎最高优先级取数口。
     *
     * @param customerId 客户 ID
     * @param materialId 物料 ID
     * @return 生效特价；无则 null
     */
    SalPriceSpecial findEffective(Long customerId, Long materialId);
}

package com.erp.basedata.service;

import com.erp.basedata.dto.form.SupplierAdmitForm;
import com.erp.basedata.dto.vo.SupplierAdmitVO;
import com.erp.common.result.PageResult;

/**
 * 供应商准入 Service（V141）。
 * <p>
 * 登记 → 提交申请（workflow bizType=supplier_admit 或管理员简单审批）→
 * 通过（base_supplier 启用）/ 驳回 / 黑名单（base_supplier.is_blacklist=1，
 * 采购下单拦截）。
 */
public interface SupplierAdmitService {

    /**
     * 分页查询（查询时惰性同步 workflow 审批结果 10→20）。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param keyword  关键词（供应商名称/信用代码模糊）
     * @param status   准入状态（可空）
     * @return 分页结果
     */
    PageResult<SupplierAdmitVO> page(long pageNum, long pageSize, String keyword, Integer status);

    /**
     * 详情。
     */
    SupplierAdmitVO getById(Long id);

    /**
     * 登记/编辑（id 空=新登记，自动创建停用 base_supplier 并生成 GYS 编码）。
     *
     * @param form 表单（submit=true 时登记后立即提交申请）
     * @return 申请 ID
     */
    Long save(SupplierAdmitForm form);

    /**
     * 提交申请（0草稿/30驳回 → 10待审，自动尝试挂审批流）。
     */
    void submit(Long id);

    /**
     * 管理员通过（10→20，同时启用 base_supplier）。
     */
    void approve(Long id, String remark);

    /**
     * 管理员驳回（10→30，base_supplier 保持停用）。
     */
    void reject(Long id, String remark);

    /**
     * 拉黑（20通过 → 40黑名单，base_supplier 停用 + is_blacklist=1）。
     *
     * @param id     申请 ID
     * @param reason 拉黑原因
     */
    void blacklist(Long id, String reason);

    /**
     * 移出黑名单（40 → 20，恢复启用 is_blacklist=0）。
     */
    void unblacklist(Long id);

    /**
     * 删除申请（仅草稿/驳回可删，同时逻辑删登记建的 base_supplier）。
     */
    void delete(Long id);
}

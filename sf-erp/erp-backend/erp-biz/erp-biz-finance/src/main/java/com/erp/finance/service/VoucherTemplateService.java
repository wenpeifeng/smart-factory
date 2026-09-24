package com.erp.finance.service;

import com.erp.finance.dto.form.VoucherTemplateForm;
import com.erp.finance.dto.vo.VoucherTemplateVO;

import java.util.List;

/**
 * 凭证模板管理服务（V521）：八类业务单据的凭证分录规则 CRUD。
 */
public interface VoucherTemplateService {

    /**
     * 模板列表（全部，含停用，按业务类型排序）。
     *
     * @return 模板列表
     */
    List<VoucherTemplateVO> list();

    /**
     * 新增模板（uk(biz_type)：同类型已存在将被拒绝）。
     *
     * @param form 表单
     * @return 模板 ID
     */
    Long create(VoucherTemplateForm form);

    /**
     * 修改模板（规则 JSON 结构校验；bizType 不可改——uk 键）。
     *
     * @param id   模板 ID
     * @param form 表单
     */
    void update(Long id, VoucherTemplateForm form);

    /**
     * 删除模板（逻辑删；建议用停用替代，保留历史模板可追溯）。
     *
     * @param id 模板 ID
     */
    void delete(Long id);
}

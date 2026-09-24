package com.erp.qc.service;

import com.erp.common.result.PageResult;
import com.erp.qc.dto.form.QcTemplateForm;
import com.erp.qc.dto.query.QcTemplateQuery;
import com.erp.qc.dto.vo.QcTemplateVO;

/**
 * 检验模板 Service：模板 + 检验项目子表的维护。
 */
public interface QcTemplateService {

    /**
     * 分页查询模板。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<QcTemplateVO> page(QcTemplateQuery query);

    /**
     * 模板详情（含检验项目子表）。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws com.erp.common.exception.BusinessException 模板不存在
     */
    QcTemplateVO getDetail(Long id);

    /**
     * 新增模板（明细整体插入）。
     *
     * @param form 模板表单
     * @return 新模板 ID
     * @throws com.erp.common.exception.BusinessException 编码重复或上下限倒挂
     */
    Long create(QcTemplateForm form);

    /**
     * 修改模板（明细整体覆盖式重建）。
     *
     * @param id      模板 ID
     * @param form    模板表单
     * @throws com.erp.common.exception.BusinessException 模板不存在/编码重复/上下限倒挂
     */
    void update(Long id, QcTemplateForm form);

    /**
     * 删除模板（逻辑删除，明细一并逻辑删；已生成检验单的明细为快照不受影响）。
     *
     * @param id 模板 ID
     * @throws com.erp.common.exception.BusinessException 模板不存在
     */
    void delete(Long id);
}

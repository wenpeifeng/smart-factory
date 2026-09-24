package com.mes.quality.service;

import com.mes.common.result.PageResult;
import com.mes.quality.dto.form.QcTemplateForm;
import com.mes.quality.dto.vo.QcTemplateVO;

/**
 * 检验模板服务（IQC/IPQC/FQC 通用模板维护）。
 */
public interface QcTemplateService {

    /**
     * 分页查询模板。
     *
     * @param pageNum      页码
     * @param pageSize     页大小
     * @param keyword      编码/名称模糊
     * @param inspectType  IQC/IPQC/FQC
     * @return 分页结果
     */
    PageResult<QcTemplateVO> page(long pageNum, long pageSize, String keyword, String inspectType);

    /**
     * 模板详情（含项目行）。
     *
     * @param id 模板 id
     * @return 详情 VO
     */
    QcTemplateVO detail(Long id);

    /**
     * 创建模板（项目行一起入库）。
     *
     * @param form 表单
     * @return 模板 id
     */
    Long create(QcTemplateForm form);

    /**
     * 修改模板（项目行全删全插）。
     *
     * @param id   模板 id
     * @param form 表单
     */
    void update(Long id, QcTemplateForm form);

    /**
     * 删除模板。
     *
     * @param id 模板 id
     */
    void delete(Long id);

    /**
     * 启用/停用模板。
     *
     * @param id     模板 id
     * @param status 1 启用 / 0 停用
     */
    void changeStatus(Long id, Integer status);
}

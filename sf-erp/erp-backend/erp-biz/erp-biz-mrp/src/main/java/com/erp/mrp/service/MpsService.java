package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.MpsForm;
import com.erp.mrp.dto.vo.MpsVO;

/**
 * 主生产计划 MPS 服务 —— MRP 运算的需求来源维护。
 */
public interface MpsService {

    /**
     * 分页查询 MPS。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  成品物料编码/名称关键词，可空
     * @param status   状态过滤，可空
     * @return MPS 分页（含物料回显）
     */
    PageResult<MpsVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * 新建 MPS 行（待运算状态）。
     *
     * @param form 表单
     * @return 新行ID
     * @throws com.erp.common.exception.BusinessException 成品物料不存在
     */
    Long create(MpsForm form);

    /**
     * 修改 MPS 行。
     *
     * @param id   行ID
     * @param form 表单
     * @throws com.erp.common.exception.BusinessException 不存在 / 物料不存在
     */
    void update(Long id, MpsForm form);

    /**
     * 删除 MPS 行。
     *
     * @param id 行ID
     * @throws com.erp.common.exception.BusinessException 不存在
     */
    void delete(Long id);
}

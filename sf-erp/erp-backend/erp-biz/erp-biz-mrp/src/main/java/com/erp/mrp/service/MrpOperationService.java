package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.OperationForm;
import com.erp.mrp.dto.vo.OperationVO;

import java.util.List;

/**
 * 工序定义服务 —— 标准工序库（供工艺路线步骤引用）。
 */
public interface MrpOperationService {

    /**
     * 分页查询工序。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  编码/名称关键词，可空
     * @param status   状态过滤，可空
     * @return 工序分页
     */
    PageResult<OperationVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * 全部工序（选择器用，按编码升序）。
     *
     * @return 工序列表
     */
    List<OperationVO> listAll();

    /**
     * 新建工序（编码 OP+流水自动生成）。
     *
     * @param form 表单
     * @return 新工序ID
     * @throws com.erp.common.exception.BusinessException 默认工作中心不存在
     */
    Long create(OperationForm form);

    /**
     * 修改工序。
     *
     * @param id   工序ID
     * @param form 表单
     * @throws com.erp.common.exception.BusinessException 不存在 / 默认工作中心不存在
     */
    void update(Long id, OperationForm form);

    /**
     * 删除工序（逻辑删除；被工艺路线步骤引用时拒绝）。
     *
     * @param id 工序ID
     * @throws com.erp.common.exception.BusinessException 不存在 / 已被引用
     */
    void delete(Long id);
}

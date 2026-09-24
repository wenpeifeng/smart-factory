package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.WorkCenterForm;
import com.erp.mrp.dto.vo.WorkCenterVO;

import java.util.List;

/**
 * 工作中心服务 —— 车间作业管理的能力计量单元基础资料。
 */
public interface MrpWorkCenterService {

    /**
     * 分页查询工作中心。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  编码/名称/车间关键词，可空
     * @param status   状态过滤，可空
     * @return 工作中心分页
     */
    PageResult<WorkCenterVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * 全部工作中心（选择器用，按编码升序）。
     *
     * @return 工作中心列表
     */
    List<WorkCenterVO> listAll();

    /**
     * 新建工作中心（编码 WC+流水自动生成）。
     *
     * @param form 表单
     * @return 新工作中心ID
     */
    Long create(WorkCenterForm form);

    /**
     * 修改工作中心。
     *
     * @param id   工作中心ID
     * @param form 表单
     * @throws com.erp.common.exception.BusinessException 不存在
     */
    void update(Long id, WorkCenterForm form);

    /**
     * 删除工作中心（逻辑删除；被工序/工艺路线/工单工序引用时拒绝）。
     *
     * @param id 工作中心ID
     * @throws com.erp.common.exception.BusinessException 不存在 / 已被引用
     */
    void delete(Long id);
}

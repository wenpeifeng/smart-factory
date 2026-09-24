package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.IssueForm;
import com.erp.mrp.dto.vo.MaterialIssueVO;

/**
 * 生产领料服务 —— 按 BOM 自动带出应领数量，审核扣库存。
 */
public interface MrpMaterialIssueService {

    /**
     * 分页查询领料单。
     *
     * @param pageNum     页码
     * @param pageSize    每页条数
     * @param workOrderId 生产订单ID过滤，可空
     * @param status      状态过滤，可空
     * @return 领料单分页
     */
    PageResult<MaterialIssueVO> page(long pageNum, long pageSize, Long workOrderId, String status);

    /**
     * 领料单详情（含明细）。
     *
     * @param id 领料单ID
     * @return 领料单 VO
     * @throws com.erp.common.exception.BusinessException 不存在
     */
    MaterialIssueVO detail(Long id);

    /**
     * 创建草稿领料单：items 为空时按 BOM 自动带出全部子件（实领=应领）。
     *
     * @param form 表单
     * @return 新领料单ID
     * @throws com.erp.common.exception.BusinessException 订单状态不合法 / 超领 / 明细物料不在BOM内
     */
    Long create(IssueForm form);

    /**
     * 修改草稿领料单（明细整体重建，校验同创建）。
     *
     * @param id   领料单ID
     * @param form 表单
     * @throws com.erp.common.exception.BusinessException 非草稿 / 校验失败
     */
    void update(Long id, IssueForm form);

    /**
     * 审核领料单：逐子件条件扣减 inv_stock（不足抛异常回滚），
     * 写 inv_movement(PROD_USE)，订单进入生产中。
     *
     * @param id 领料单ID
     * @throws com.erp.common.exception.BusinessException 库存不足 / 状态不合法 / 库存表未就绪
     */
    void audit(Long id);

    /**
     * 删除草稿领料单。
     *
     * @param id 领料单ID
     * @throws com.erp.common.exception.BusinessException 非草稿
     */
    void delete(Long id);
}

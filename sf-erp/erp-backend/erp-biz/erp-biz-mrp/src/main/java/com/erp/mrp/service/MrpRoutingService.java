package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.RoutingCopyForm;
import com.erp.mrp.dto.form.RoutingForm;
import com.erp.mrp.dto.vo.RoutingTreeVO;
import com.erp.mrp.dto.vo.RoutingVO;
import com.erp.mrp.entity.MrpRouting;

/**
 * 工艺路线服务 —— 头 + 工序步骤序列的 CRUD、复制新版本、审核/停用、树形查看。
 */
public interface MrpRoutingService {

    /**
     * 分页查询工艺路线。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  成品物料编码/名称关键词，可空
     * @param status   状态过滤，可空
     * @return 工艺路线分页（含步骤数）
     */
    PageResult<RoutingVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * 工艺路线详情（含按序号升序的步骤明细）。
     *
     * @param id 工艺路线ID
     * @return 工艺路线 VO
     * @throws com.erp.common.exception.BusinessException 不存在
     */
    RoutingVO detail(Long id);

    /**
     * 树形查看 —— 线性工艺路线的工序序列（含工序/工作中心回显与生效单件工时）。
     *
     * @param id 工艺路线ID
     * @return 工序序列树 VO
     * @throws com.erp.common.exception.BusinessException 不存在
     */
    RoutingTreeVO tree(Long id);

    /**
     * 新建工艺路线（草稿）。
     *
     * @param form 表单
     * @return 新工艺路线ID
     * @throws com.erp.common.exception.BusinessException 物料/工序/工作中心不存在 / 序号不连续 / 版本重复
     */
    Long create(RoutingForm form);

    /**
     * 修改工艺路线（仅草稿；步骤整体重建）。
     *
     * @param id   工艺路线ID
     * @param form 表单
     * @throws com.erp.common.exception.BusinessException 非草稿状态 / 校验失败
     */
    void update(Long id, RoutingForm form);

    /**
     * 删除工艺路线（仅草稿/停用；步骤级联逻辑删除）。
     *
     * @param id 工艺路线ID
     * @throws com.erp.common.exception.BusinessException 已审核状态不可删除
     */
    void delete(Long id);

    /**
     * 复制为新版本（步骤一同复制，状态草稿）。
     *
     * @param id   源工艺路线ID
     * @param form 新版本号表单
     * @return 新工艺路线ID
     * @throws com.erp.common.exception.BusinessException 新版本号已存在
     */
    Long copy(Long id, RoutingCopyForm form);

    /**
     * 审核工艺路线：DRAFT → ACTIVE（须至少一个步骤）。
     *
     * @param id 工艺路线ID
     * @throws com.erp.common.exception.BusinessException 非草稿 / 无步骤
     */
    void audit(Long id);

    /**
     * 停用工艺路线：ACTIVE → DISABLED。
     *
     * @param id 工艺路线ID
     * @throws com.erp.common.exception.BusinessException 非已审核状态
     */
    void disable(Long id);

    /**
     * 取成品物料最新一条已审核(ACTIVE)工艺路线（生产订单下达联动用）。
     *
     * @param materialId 成品物料ID
     * @return 工艺路线，无则 null
     */
    MrpRouting findLatestActive(Long materialId);
}

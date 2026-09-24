package com.mes.plan.service;

import com.mes.common.result.PageResult;
import com.mes.plan.dto.form.WorkorderForm;
import com.mes.plan.dto.query.WorkorderPageQuery;
import com.mes.plan.dto.vo.WorkorderDetailVO;
import com.mes.plan.dto.vo.WorkorderVO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产工单服务。
 * <p>状态机：DRAFT →(release) RELEASED →(首笔过站报工) PRODUCING →(finish) FINISHED →(close) CLOSED。
 * 用料行与工序行在 release 时按 BOM/工艺路线展开，之后不可再改。
 */
public interface WorkorderService {

    /**
     * 分页查询工单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<WorkorderVO> page(WorkorderPageQuery query);

    /**
     * 工单详情（含工序行、用料行）。
     *
     * @param id 工单 id
     * @return 详情 VO
     */
    WorkorderDetailVO detail(Long id);

    /**
     * 手工创建工单（DRAFT）。
     *
     * @param form 表单
     * @return 工单 id
     */
    Long create(WorkorderForm form);

    /**
     * 修改工单（仅 DRAFT）。
     *
     * @param id   工单 id
     * @param form 表单
     */
    void update(Long id, WorkorderForm form);

    /**
     * 删除工单（仅 DRAFT，级联逻辑删子表）。
     *
     * @param id 工单 id
     */
    void delete(Long id);

    /**
     * 下达工单：DRAFT → RELEASED，按 BOM 展开用料、按工艺路线生成工序。
     *
     * @param id 工单 id
     */
    void release(Long id);

    /**
     * 完工：RELEASED/PRODUCING → FINISHED。
     *
     * @param id 工单 id
     */
    void finish(Long id);

    /**
     * 关闭：FINISHED → CLOSED（或 RELEASED/PRODUCING 强制结案）。
     *
     * @param id          工单 id
     * @param closeRemark 结案说明
     */
    void close(Long id, String closeRemark);

    /**
     * ERP 生产订单下推幂等建单（integration 入站调用）。
     *
     * @param orderNo     ERP 生产订单号（幂等键）
     * @param materialId  成品物料 id
     * @param qty         计划数量
     * @param bomVersion  BOM 版本（空=唯一 ACTIVE）
     * @param routingId   工艺路线 id（空=ACTIVE）
     * @param warehouseId 完工入库仓库 id
     * @param snMode      追踪模式
     * @param planStart   计划开工日期
     * @param planEnd     计划完工日期
     * @param sourceId    ERP 侧单据 id
     * @param remark      备注
     * @return 工单 id（已存在则返回已有工单）
     */
    Long receiveFromErp(String orderNo, Long materialId, BigDecimal qty, String bomVersion,
                        Long routingId, Long warehouseId, String snMode,
                        LocalDate planStart, LocalDate planEnd, Long sourceId, String remark);

    /**
     * 过站报工回写工单累计数（execution 模块调用；首笔报工将工单置 PRODUCING）。
     *
     * @param workorderId 工单 id
     * @param goodQty     本笔合格数
     * @param scrapQty    本笔报废数
     */
    void accumulateReport(Long workorderId, java.math.BigDecimal goodQty, java.math.BigDecimal scrapQty);
}

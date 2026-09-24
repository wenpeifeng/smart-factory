package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.purchase.dto.query.PurOrderQuery;
import com.erp.purchase.dto.vo.PurOrderVO;
import com.erp.purchase.entity.PurOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 采购订单 Mapper。
 * 分页与详情的关联查询（供应商/仓库/物料名称）见 resources/mapper/purchase/PurOrderMapper.xml。
 */
public interface PurOrderMapper extends BaseMapper<PurOrder> {

    /**
     * 分页查询采购订单（JOIN base_supplier / base_warehouse 带出名称）。
     *
     * @param page MP 分页参数（由分页插件注入 LIMIT）
     * @param q    查询条件
     * @return 订单 VO 分页
     */
    IPage<PurOrderVO> selectOrderPage(Page<PurOrderVO> page, @Param("q") PurOrderQuery q);

    /**
     * 审核：0草稿 -> 10已审核。
     * 条件 UPDATE 保证并发下状态机只允许正向流转，返回受影响行数（0=当前状态不可审核）。
     *
     * @param id      订单 ID
     * @param auditBy 审核人（当前登录用户 ID，未登录上下文为 0=系统）
     * @return 受影响行数
     */
    @Update("UPDATE pur_order SET status = 10, audit_by = #{auditBy}, audit_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 0")
    int audit(@Param("id") Long id, @Param("auditBy") Long auditBy);

    /**
     * 关闭：10已审核/20部分入库 -> 90已关闭（终态，不允许再入库）。
     * 已发起过审批流的草稿（wf_started=1，被驳回或审批中）也允许关闭作废——
     * 审批流三段式约定"驳回后允许关闭单据"；关闭后即便流程在途通过，
     * 审核入口也会因状态非草稿而拦截，不会出现"关闭单据被审核"的不一致。
     *
     * @param id 订单 ID
     * @return 受影响行数（0=当前状态不可关闭）
     */
    @Update("UPDATE pur_order SET status = 90, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 "
            + "AND (status IN (10, 20) OR (status = 0 AND wf_started = 1))")
    int close(@Param("id") Long id);

    /**
     * 入库/退货审核后回写订单进度状态（10/20/30 之间按累计入库数量推进；
     * 退货导致不再全部入库时允许 30 -> 20 回落，业务上"完成态失效"）。
     *
     * @param id     订单 ID
     * @param status 目标状态（20部分入库 / 30已完成）
     * @return 受影响行数
     */
    @Update("UPDATE pur_order SET status = #{status}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status IN (10, 20, 30)")
    int updateProgressStatus(@Param("id") Long id, @Param("status") int status);
}

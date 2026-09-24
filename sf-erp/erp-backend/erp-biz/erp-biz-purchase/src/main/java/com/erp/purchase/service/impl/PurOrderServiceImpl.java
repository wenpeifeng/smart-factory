package com.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.purchase.dto.form.PurOrderForm;
import com.erp.purchase.dto.query.PurOrderQuery;
import com.erp.purchase.dto.vo.PurOrderVO;
import com.erp.purchase.entity.PurOrder;
import com.erp.purchase.entity.PurOrderDetail;
import com.erp.purchase.mapper.BasedataMapper;
import com.erp.purchase.mapper.PurOrderDetailMapper;
import com.erp.purchase.mapper.PurOrderMapper;
import com.erp.purchase.service.OrderNoGenerator;
import com.erp.purchase.service.PurOrderService;
import com.erp.workflow.dto.vo.StartResultVO;
import com.erp.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采购订单 Service 实现。
 * <p>
 * 状态机约束：审核/关闭用条件 UPDATE（WHERE status=...）落地，
 * 并发重复提交时第二次更新影响 0 行直接报错，不会出现跳跃流转。
 * 金额：明细 amount 与主表 totalAmount 一律服务端重算（HALF_UP 2 位）。
 * <p>
 * 审核为<b>三段式</b>（审批流接入，见 workflow.md 第 3 节约定）：
 * <ol>
 *   <li>首次点审核：调 {@link WorkflowService#startFlow}（bizType=pur_order，
 *       vars.amount=价税合计）。SKIPPED/AGREED -> 执行原审核；RUNNING -> 不执行，
 *       置 wf_started=1 并返回"已提交审批"（单据保持草稿态）；</li>
 *   <li>审批中重复点审核：startFlow 幂等返回 RUNNING -> 仍提示"已提交审批"；</li>
 *   <li>审批通过后再点审核：wf_started=1 且 isApproved=true -> 执行原审核；</li>
 *   <li>驳回（REJECTED）后再点审核：isApproved=false -> 重新 startFlow（=新实例重新提交）；</li>
 *   <li>驳回/审批中的草稿单据允许"关闭"作废（close 条件含 wf_started=1 的草稿）。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PurOrderServiceImpl implements PurOrderService {

    /** 审批流单据类型（dict:wf_biz_type，与 erp-workflow 白名单一致） */
    private static final String WF_BIZ_TYPE = "pur_order";

    /** 审批流条件变量标准名（与节点条件表达式 amount>10000 对齐） */
    private static final String WF_VAR_AMOUNT = "amount";

    /** 发起结果状态：RUNNING=审批中（不执行原审核） */
    private static final String WF_STATUS_RUNNING = "RUNNING";

    private final PurOrderMapper orderMapper;
    private final PurOrderDetailMapper orderDetailMapper;
    private final BasedataMapper basedataMapper;
    private final OrderNoGenerator orderNoGenerator;
    /** 审批引擎（三段式审核，SKIPPED 时免审直过保持一期行为） */
    private final WorkflowService workflowService;

    @Override
    public PageResult<PurOrderVO> page(PurOrderQuery query) {
        var page = orderMapper.selectOrderPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PurOrderVO detail(Long id) {
        PurOrder order = requireOrder(id);
        PurOrderVO vo = toVo(order);
        vo.setDetails(orderDetailMapper.selectDetailsWithMaterial(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurOrderForm form) {
        validateBaseInfo(form.getSupplierId(), form.getWarehouseId());
        List<PurOrderDetail> details = buildDetails(form);
        PurOrder order = new PurOrder();
        order.setOrderNo(orderNoGenerator.next(OrderNoGenerator.PREFIX_PUR_ORDER));
        order.setSupplierId(form.getSupplierId());
        order.setWarehouseId(form.getWarehouseId());
        order.setOrderDate(form.getOrderDate());
        order.setStatus(PurOrder.STATUS_DRAFT);
        order.setRemark(form.getRemark());
        order.setTotalAmount(totalOf(details));
        orderMapper.insert(order);
        insertDetails(order.getId(), details);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurOrderForm form) {
        PurOrder order = requireOrder(id);
        if (order.getStatus() != PurOrder.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.po-only-draft-modify", order.getStatus());
        }
        validateBaseInfo(form.getSupplierId(), form.getWarehouseId());
        List<PurOrderDetail> details = buildDetails(form);
        // 修改即整体重写明细：逻辑删旧行 + 插新行，避免做差量合并的复杂度
        orderDetailMapper.delete(Wrappers.lambdaQuery(PurOrderDetail.class)
                .eq(PurOrderDetail::getOrderId, id));
        order.setSupplierId(form.getSupplierId());
        order.setWarehouseId(form.getWarehouseId());
        order.setOrderDate(form.getOrderDate());
        order.setRemark(form.getRemark());
        order.setTotalAmount(totalOf(details));
        orderMapper.updateById(order);
        insertDetails(id, details);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String audit(Long id) {
        PurOrder order = requireOrder(id);
        if (order.getStatus() != PurOrder.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.po-only-draft-audit", order.getStatus());
        }
        if (order.getSupplierId() == null) {
            // MRP 转单等场景生成的草稿允许供应商待定，但审核（进入在途/应付链路）前必须补填
            throw BusinessException.ofKey("erp.pur.po-supplier-required", order.getOrderNo());
        }
        checkSupplierNotBlacklisted(order.getSupplierId());
        Map<String, Object> vars = Map.of(WF_VAR_AMOUNT,
                order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount());
        boolean started = order.getWfStarted() != null && order.getWfStarted() == 1;
        if (!started) {
            // 首次审核：发起流程（无启用流程 SKIPPED / 条件全不满足 AGREED -> 免审直过）
            StartResultVO r = workflowService.startFlow(WF_BIZ_TYPE, id, order.getOrderNo(), vars);
            if (WF_STATUS_RUNNING.equals(r.getStatus())) {
                markWfStarted(id, true);
                return "已提交审批";
            }
        } else if (workflowService.isApproved(WF_BIZ_TYPE, id)) {
            // 曾发起过且最新实例已 AGREED（审批人已通过）-> 执行原审核
            doAudit(id);
            return "审批已通过，订单审核完成";
        } else {
            // RUNNING（幂等提示）或 REJECTED（驳回后重新提交=新实例）
            StartResultVO r = workflowService.startFlow(WF_BIZ_TYPE, id, order.getOrderNo(), vars);
            if (WF_STATUS_RUNNING.equals(r.getStatus())) {
                return "已提交审批";
            }
        }
        doAudit(id);
        return "审核成功";
    }

    /** 原审核逻辑（条件 UPDATE 0草稿->10已审核，audit_by=当前登录用户） */
    private void doAudit(Long id) {
        int rows = orderMapper.audit(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            // 区分"不存在"与"状态不允许"给用户准确提示
            requireOrder(id);
            throw BusinessException.ofKey("erp.pur.po-only-draft-audit-plain");
        }
    }

    /** 置审批流发起标记（三段式第二/三段的判定依据） */
    private void markWfStarted(Long id, boolean started) {
        PurOrder upd = new PurOrder();
        upd.setId(id);
        upd.setWfStarted(started ? 1 : 0);
        orderMapper.updateById(upd);
    }

    @Override
    public void close(Long id) {
        int rows = orderMapper.close(id);
        if (rows == 0) {
            requireOrder(id);
            throw BusinessException.ofKey("erp.pur.po-close-status-not-allowed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurOrder order = requireOrder(id);
        if (order.getStatus() != PurOrder.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.po-only-draft-delete", order.getStatus());
        }
        orderMapper.deleteById(id);
        orderDetailMapper.delete(Wrappers.lambdaQuery(PurOrderDetail.class)
                .eq(PurOrderDetail::getOrderId, id));
    }

    @Override
    public void refreshProgressStatus(Long orderId) {
        PurOrder order = requireOrder(orderId);
        if (order.getStatus() == PurOrder.STATUS_CLOSED) {
            // 已关闭订单是终态，不允许任何库存回写（入库/退货审核在生成阶段已拦截，此处双保险）
            throw BusinessException.ofKey("erp.pur.po-closed-no-writeback", order.getOrderNo());
        }
        List<PurOrderDetail> details = orderDetailMapper.selectList(
                Wrappers.lambdaQuery(PurOrderDetail.class).eq(PurOrderDetail::getOrderId, orderId));
        boolean allReceived = details.stream()
                .allMatch(d -> d.getReceivedQty().compareTo(d.getQty()) >= 0);
        // 允许 30->20 回落：退货后不再"全部入库"，完成态失效（金蝶同款处理）
        orderMapper.updateProgressStatus(orderId,
                allReceived ? PurOrder.STATUS_FINISHED : PurOrder.STATUS_PART_INSTOCK);
    }

    /** 加载订单，不存在（含已逻辑删）抛业务异常 */
    private PurOrder requireOrder(Long id) {
        PurOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BusinessException.ofKey("erp.pur.po-not-exists", id);
        }
        return order;
    }

    /** 校验供应商/仓库在用（直查 base_* 标准表，不依赖 basedata Java 代码）。
     * 供应商可空：MRP 转单等场景草稿可先落库后补填（审核前强校验）。 */
    private void validateBaseInfo(Long supplierId, Long warehouseId) {
        if (supplierId != null && basedataMapper.countSupplierById(supplierId) == 0) {
            throw BusinessException.ofKey("erp.pur.supplier-not-exists", supplierId);
        }
        if (supplierId != null) {
            checkSupplierNotBlacklisted(supplierId);
        }
        if (warehouseId != null && basedataMapper.countWarehouseById(warehouseId) == 0) {
            throw BusinessException.ofKey("erp.pur.warehouse-not-exists", warehouseId);
        }
    }

    /** 黑名单供应商下单/审核拦截（V141 供应商准入，最小侵入校验）。 */
    private void checkSupplierNotBlacklisted(Long supplierId) {
        if (supplierId != null
                && Integer.valueOf(1).equals(basedataMapper.selectSupplierBlacklist(supplierId))) {
            throw BusinessException.ofKey("erp.biz.sup-blacklisted", supplierId);
        }
    }

    /**
     * 构建订单明细实体列表（校验物料在用、金额 = 数量×含税单价 HALF_UP 2 位）。
     * 此时 orderId 尚未回填，由 insertDetails 统一填充后落库。
     */
    private List<PurOrderDetail> buildDetails(PurOrderForm form) {
        return form.getDetails().stream()
                .map(line -> {
                    if (basedataMapper.countMaterialById(line.getMaterialId()) == 0) {
                        throw BusinessException.ofKey("erp.pur.material-not-exists", line.getMaterialId());
                    }
                    PurOrderDetail d = new PurOrderDetail();
                    d.setMaterialId(line.getMaterialId());
                    d.setQty(line.getQty());
                    d.setPrice(line.getPrice());
                    d.setTaxRate(line.getTaxRate() == null ? BigDecimal.ZERO : line.getTaxRate());
                    d.setAmount(DocCalc.lineAmount(line.getQty(), line.getPrice()));
                    d.setDeliveryDate(line.getDeliveryDate());
                    d.setReceivedQty(BigDecimal.ZERO);
                    d.setRemark(line.getRemark());
                    return d;
                })
                .toList();
    }

    /** 回填 orderId 后批量插入明细 */
    private void insertDetails(Long orderId, List<PurOrderDetail> details) {
        details.forEach(d -> {
            d.setOrderId(orderId);
            orderDetailMapper.insert(d);
        });
    }

    /** 价税合计 = 明细金额之和 */
    private BigDecimal totalOf(List<PurOrderDetail> details) {
        return DocCalc.total(details.stream().map(PurOrderDetail::getAmount).toList());
    }

    /** 实体 -> VO（列表/详情通用，明细由调用方按需填充） */
    private PurOrderVO toVo(PurOrder order) {
        PurOrderVO vo = new PurOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setSupplierId(order.getSupplierId());
        vo.setSupplierName(basedataMapper.selectSupplierName(order.getSupplierId()));
        vo.setWarehouseId(order.getWarehouseId());
        vo.setWarehouseName(order.getWarehouseId() == null
                ? null : basedataMapper.selectWarehouseName(order.getWarehouseId()));
        vo.setOrderDate(order.getOrderDate());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setAuditTime(order.getAuditTime());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    /** 供入库单生成校验使用：订单是否处于可入库状态（已审核/部分入库） */
    static boolean canInstock(PurOrder order) {
        return Objects.equals(order.getStatus(), PurOrder.STATUS_AUDITED)
                || Objects.equals(order.getStatus(), PurOrder.STATUS_PART_INSTOCK);
    }
}

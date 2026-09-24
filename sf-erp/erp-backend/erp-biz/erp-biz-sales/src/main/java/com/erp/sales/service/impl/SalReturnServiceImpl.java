package com.erp.sales.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.cost.service.CostService;
import com.erp.framework.security.SecurityUtils;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.service.StockService;
import com.erp.sales.constant.SalesConstants;
import com.erp.sales.dto.form.SalReturnDetailForm;
import com.erp.sales.dto.form.SalReturnForm;
import com.erp.sales.dto.vo.SalReturnDetailBundle;
import com.erp.sales.dto.vo.SalReturnDetailVO;
import com.erp.sales.dto.vo.SalReturnVO;
import com.erp.sales.entity.SalOutstock;
import com.erp.sales.entity.SalOutstockDetail;
import com.erp.sales.entity.SalReturn;
import com.erp.sales.entity.SalReturnDetail;
import com.erp.sales.mapper.SalOutstockDetailMapper;
import com.erp.sales.mapper.SalOutstockMapper;
import com.erp.sales.mapper.SalReturnDetailMapper;
import com.erp.sales.mapper.SalReturnMapper;
import com.erp.sales.mapper.SalesBizMapper;
import com.erp.sales.service.SalReturnService;
import com.erp.sales.service.SalesNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售退货单 Service 实现。
 * <p>
 * 审核（三模块集成接线后）事务步骤（任一步失败整体回滚）：
 * <ol>
 *   <li>条件更新单据状态 0→10（防并发重复审核，audit_by=当前登录用户）；</li>
 *   <li>逐行经 {@link StockService#stockIn} 回补库存（批次物料按表单批次号落指定批次行，
 *       非批次物料走汇总行）；</li>
 *   <li>逐行调 {@link CostService#onStockIn}（SAL_RET，amount=null 按当前移动加权价回冲）；</li>
 *   <li>生成 fin_receivable 红字应收（金额 = −单据合计，冲减客户应收余额）；</li>
 *   <li>回写出库行 returned_qty（条件防超退）。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SalReturnServiceImpl implements SalReturnService {

    /** 成本模块约定的业务类型编码（与 inventory InvBizType.SAL_RET 一致） */
    private static final String COST_BIZ_SAL_RET = "SAL_RET";

    private final SalOutstockMapper outstockMapper;
    private final SalOutstockDetailMapper outstockDetailMapper;
    private final SalReturnMapper returnMapper;
    private final SalReturnDetailMapper returnDetailMapper;
    private final SalesBizMapper bizMapper;
    private final SalesNoGenerator noGenerator;
    /** 库存唯一入口（批次感知，替代一期裸 SQL 操作 inv_stock/inv_movement） */
    private final StockService stockService;
    /** 计价唯一入口（同事务按当前移动加权价回冲） */
    private final CostService costService;

    @Override
    public PageResult<SalReturnVO> page(long pageNum, long pageSize, String keyword, Integer status) {
        Page<SalReturnVO> page = bizMapper.selectReturnPage(new Page<>(pageNum, pageSize), keyword, status);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SalReturnDetailBundle getDetail(Long id) {
        SalReturnVO ret = requireReturnVo(id);
        List<SalReturnDetailVO> details = bizMapper.selectReturnDetails(id);
        return new SalReturnDetailBundle(ret, details);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SalReturnForm form) {
        SalOutstock outstock = outstockMapper.selectById(form.getOutId());
        if (outstock == null) {
            throw BusinessException.ofKey("erp.sal.outstock-not-exists-id", form.getOutId());
        }
        if (outstock.getStatus() != SalesConstants.BIZ_AUDITED) {
            throw BusinessException.ofKey("erp.sal.outstock-not-audited-no-return", outstock.getOutNo());
        }

        // 出库行快照：退货行必须归属该出库单，数量不得超过可退数量（qty - returned_qty）
        Map<Long, SalOutstockDetail> outLines = loadOutLines(outstock.getId());
        List<SalReturnDetail> details = new ArrayList<>(form.getDetails().size());
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalReturnDetailForm d : form.getDetails()) {
            SalOutstockDetail line = outLines.get(d.getOutDetailId());
            if (line == null) {
                throw BusinessException.ofKey("erp.sal.outstock-line-not-exists", d.getOutDetailId(), outstock.getOutNo());
            }
            BigDecimal returnable = line.getQty().subtract(line.getReturnedQty());
            if (d.getQty().compareTo(returnable) > 0) {
                throw BusinessException.ofKey("erp.sal.return-over-returnable", line.getMaterialId(), d.getQty().toPlainString(), returnable.toPlainString());
            }
            SalReturnDetail detail = new SalReturnDetail();
            detail.setOutDetailId(line.getId());
            detail.setMaterialId(line.getMaterialId());
            detail.setBatchNo(d.getBatchNo() == null ? "" : d.getBatchNo().trim());
            detail.setQty(d.getQty());
            detail.setPrice(line.getPrice());
            BigDecimal amount = d.getQty().multiply(line.getPrice()).setScale(2, RoundingMode.HALF_UP);
            detail.setAmount(amount);
            details.add(detail);
            totalQty = totalQty.add(d.getQty());
            totalAmount = totalAmount.add(amount);
        }

        SalReturn ret = new SalReturn();
        ret.setReturnNo(noGenerator.next(SalesConstants.NO_PREFIX_RETURN,
                () -> returnMapper.selectCount(Wrappers.lambdaQuery(SalReturn.class)
                        .likeRight(SalReturn::getReturnNo, todayPrefix()))));
        ret.setOutId(outstock.getId());
        ret.setOutNo(outstock.getOutNo());
        ret.setCustomerId(outstock.getCustomerId());
        ret.setWarehouseId(outstock.getWarehouseId());
        ret.setReturnDate(form.getReturnDate());
        ret.setTotalQty(totalQty.setScale(4, RoundingMode.HALF_UP));
        ret.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        ret.setStatus(SalesConstants.BIZ_DRAFT);
        ret.setRemark(form.getRemark());
        returnMapper.insert(ret);

        for (SalReturnDetail detail : details) {
            detail.setReturnId(ret.getId());
            returnDetailMapper.insert(detail);
        }
        return ret.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SalReturn ret = requireReturn(id);
        if (ret.getStatus() != SalesConstants.BIZ_DRAFT) {
            throw BusinessException.ofKey("erp.sal.return-only-draft-audit");
        }

        // 1. 状态条件流转（0→10），防并发重复审核
        int rows = bizMapper.auditReturn(id, SecurityUtils.getCurrentUserId());
        if (rows == 0) {
            throw BusinessException.ofKey("erp.sal.return-status-changed-audit-failed");
        }

        List<SalReturnDetail> details = returnDetailMapper.selectList(
                Wrappers.lambdaQuery(SalReturnDetail.class).eq(SalReturnDetail::getReturnId, id));

        // 2. StockService 批次入库（批次物料按表单批次号回补指定批次行；非批次汇总行）
        //    + 3. CostService 按当前移动加权价回冲（SAL_RET）
        for (SalReturnDetail d : details) {
            stockService.stockIn(ret.getWarehouseId(), d.getMaterialId(), d.getBatchNo(),
                    d.getQty(), InvBizType.SAL_RET, id, ret.getReturnNo(),
                    "销售退货 " + ret.getReturnNo());
            // 退货回冲不携带金额：按当前移动加权单价估算入账（与 cost 模块约定一致）
            costService.onStockIn(d.getMaterialId(), ret.getWarehouseId(), COST_BIZ_SAL_RET,
                    ret.getReturnNo(), id, d.getQty(), null, ret.getReturnDate());
        }

        // 4. 生成红字应收（负金额冲减客户未收余额）
        bizMapper.insertReceivable(ret.getCustomerId(), ret.getReturnNo(),
                ret.getReturnDate(), ret.getTotalAmount().negate(),
                SalesConstants.RECEIVABLE_UNSETTLED, "销售退货红冲 " + ret.getReturnNo());

        // 5. 回写出库行已退数量（条件更新防并发超退）
        for (SalReturnDetail d : details) {
            int updated = bizMapper.addOutstockReturnedQty(d.getOutDetailId(), d.getQty());
            if (updated == 0) {
                throw BusinessException.ofKey("erp.sal.outstock-line-returnable-insufficient", d.getOutDetailId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalReturn ret = requireReturn(id);
        if (ret.getStatus() != SalesConstants.BIZ_DRAFT) {
            throw BusinessException.ofKey("erp.sal.return-only-draft-delete");
        }
        returnMapper.deleteById(id);
        returnDetailMapper.delete(Wrappers.lambdaQuery(SalReturnDetail.class)
                .eq(SalReturnDetail::getReturnId, id));
    }

    // ==================== 私有方法 ====================

    /** 加载出库行并按行 ID 建索引。 */
    private Map<Long, SalOutstockDetail> loadOutLines(Long outId) {
        List<SalOutstockDetail> lines = outstockDetailMapper.selectList(
                Wrappers.lambdaQuery(SalOutstockDetail.class).eq(SalOutstockDetail::getOutId, outId));
        Map<Long, SalOutstockDetail> index = new HashMap<>(lines.size());
        for (SalOutstockDetail line : lines) {
            index.put(line.getId(), line);
        }
        return index;
    }

    private SalReturn requireReturn(Long id) {
        SalReturn ret = returnMapper.selectById(id);
        if (ret == null) {
            throw BusinessException.ofKey("erp.sal.return-not-exists", id);
        }
        return ret;
    }

    private SalReturnVO requireReturnVo(Long id) {
        SalReturnVO vo = bizMapper.selectReturn(id);
        if (vo == null) {
            throw BusinessException.ofKey("erp.sal.return-not-exists", id);
        }
        return vo;
    }

    /** 今日单号前缀（降级计数用），如 RT20260905。 */
    private String todayPrefix() {
        return SalesConstants.NO_PREFIX_RETURN
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}

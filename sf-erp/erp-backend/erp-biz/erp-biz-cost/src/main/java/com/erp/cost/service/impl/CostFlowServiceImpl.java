package com.erp.cost.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.cost.constant.CostBizType;
import com.erp.cost.constant.CostConstants;
import com.erp.cost.dto.form.HookReceiveForm;
import com.erp.cost.dto.raw.MovementRaw;
import com.erp.cost.dto.request.CostFlowQuery;
import com.erp.cost.dto.vo.CostFlowVO;
import com.erp.cost.dto.vo.HookResultVO;
import com.erp.cost.dto.vo.SyncResultVO;
import com.erp.cost.entity.CostFlow;
import com.erp.cost.entity.CostPeriod;
import com.erp.cost.mapper.CostFlowMapper;
import com.erp.cost.mapper.CostIntegrationMapper;
import com.erp.cost.mapper.CostPeriodMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.cost.service.CostFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 计价流水应用服务实现：分页查询 / sync 重算 / hook 补录。
 * <p>
 * sync 与实时过账共用 {@link CostLedger} 引擎 —— 重放时逐笔调用同一 postIn/postOut，
 * 舍入与余额推演完全一致，满足「重算结果与实时计价一致」的校验要求。
 */
@Service
@RequiredArgsConstructor
public class CostFlowServiceImpl implements CostFlowService {

    private final CostFlowMapper flowMapper;
    private final CostPeriodMapper periodMapper;
    private final CostIntegrationMapper integrationMapper;
    private final CostLedger ledger;

    @Override
    public PageResult<CostFlowVO> page(CostFlowQuery query, long pageNum, long pageSize) {
        IPage<CostFlowVO> page = flowMapper.selectFlowPage(new Page<>(pageNum, pageSize), query);
        page.getRecords().forEach(vo -> vo.setBizTypeLabel(CostFlowVO.labelOf(vo.getBizType())));
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncResultVO sync(String from, String to) {
        String toPeriod = requirePeriod(to, "to");
        String fromPeriod = from == null || from.isBlank() ? "000001" : requirePeriod(from, "from");
        if (fromPeriod.compareTo(toPeriod) > 0) {
            throw BusinessException.ofKey("erp.cost.recalc-period-order", fromPeriod, toPeriod);
        }

        // ---- 1. 已结账期间保护：窗口内不允许有 CLOSED，其后（> to）也不允许（重算会使其台账失真）----
        List<CostPeriod> closed = periodMapper.selectList(Wrappers.lambdaQuery(CostPeriod.class)
                .eq(CostPeriod::getStatus, CostConstants.PERIOD_CLOSED));
        for (CostPeriod p : closed) {
            if (p.getPeriod().compareTo(toPeriod) > 0) {
                throw BusinessException.ofKey("erp.cost.recalc-closed-exists", toPeriod, p.getPeriod());
            }
            if (p.getPeriod().compareTo(fromPeriod) >= 0) {
                throw BusinessException.ofKey("erp.cost.recalc-window-closed", fromPeriod, toPeriod, p.getPeriod());
            }
        }

        // ---- 2. 实际重算起点 = 最后一个已结账期间的次月（其台账保留、余额续算） ----
        String rebuildFrom = fromPeriod;
        for (CostPeriod p : closed) {
            String next = nextPeriod(p.getPeriod());
            if (next.compareTo(rebuildFrom) > 0) {
                rebuildFrom = next;
            }
        }

        // ---- 3. 物理删除重算窗口内的旧流水（可重建的运行台账，逻辑删除无法表达「重建」语义；
        //           窗口之后的流水不受影响） ----
        flowMapper.deleteByPeriodRange(rebuildFrom, toPeriod);

        // ---- 4. 重放 to（含）之前的全部流水：早于重算窗口的期间跳过 ——
        //           其流水要么已随结账锁定、要么属窗口外保留数据；窗口内逐笔走 CostLedger
        //           与实时过账相同的记账路径（含 PUR_IN 采购金额取数） ----
        List<MovementRaw> movements = integrationMapper.selectMovements(toPeriod);
        int processed = 0;
        int inserted = 0;
        Set<Long> materials = new HashSet<>();
        for (MovementRaw m : movements) {
            if (m.getQty() == null || m.getQty().signum() == 0 || m.getPeriod() == null) {
                continue; // 防御：0 数量流水无计价意义
            }
            processed++;
            if (m.getPeriod().compareTo(rebuildFrom) < 0) {
                continue; // 窗口之外：保留既有台账
            }
            CostBizType type = CostBizType.ofCode(m.getBizType());
            if (type == null) {
                throw BusinessException.ofKey("erp.cost.recalc-unknown-biz-type", m.getId(), m.getBizNo(), m.getBizType());
            }
            LocalDate bizDate = m.getMoveTime() == null ? null : m.getMoveTime().toLocalDate();
            if (m.getQty().signum() > 0) {
                BigDecimal amount = resolveInAmount(type, m);
                ledger.postIn(m.getMaterialId(), m.getWarehouseId(), type, safeNo(m),
                        m.getBizId(), m.getId(), m.getQty(), amount, bizDate);
            } else {
                ledger.postOut(m.getMaterialId(), m.getWarehouseId(), type, safeNo(m),
                        m.getBizId(), m.getId(), m.getQty().negate(), bizDate);
            }
            inserted++;
            materials.add(m.getMaterialId());
        }
        return new SyncResultVO(rebuildFrom, toPeriod, processed, inserted, materials.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HookResultVO receive(HookReceiveForm form) {
        CostBizType type = CostBizType.ofCode(form.getBizType());
        if (type == null) {
            throw BusinessException.ofKey("erp.cost.supplement-unknown-biz-type", form.getBizType());
        }
        if (type.isInDirection() == null) {
            // ADJUST/MOVE 双向类型无法由编码判定方向，手工补录请改用 OTH_IN/OTH_OUT 表达
            throw BusinessException.ofKey("erp.cost.supplement-direction-ambiguous", type.getCode());
        }
        LocalDate bizDate = form.getBizDate() == null ? LocalDate.now() : form.getBizDate();
        CostFlow flow = type.isInDirection()
                ? ledger.postIn(form.getMaterialId(), form.getWarehouseId(), type, form.getBizNo(),
                        null, null, form.getQty(), form.getAmount(), bizDate)
                : ledger.postOut(form.getMaterialId(), form.getWarehouseId(), type, form.getBizNo(),
                        null, null, form.getQty(), bizDate);
        return new HookResultVO(flow.getId(), flow.getPeriod(), flow.getQty(), flow.getUnitCost(),
                flow.getAmount(), flow.getBalanceQty(), flow.getBalanceCost());
    }

    /**
     * 入库金额解析：PUR_IN 取采购入库单明细金额（含税口径与应付一致）；
     * 其余入库类型返回 null → CostLedger 按当前移动加权价估算。
     */
    private BigDecimal resolveInAmount(CostBizType type, MovementRaw m) {
        if (type == CostBizType.PUR_IN && m.getBizId() != null) {
            return integrationMapper.sumPurchaseInAmount(m.getBizId(), m.getMaterialId());
        }
        return null;
    }

    private String safeNo(MovementRaw m) {
        return m.getBizNo() == null ? "MV" + m.getId() : m.getBizNo();
    }

    /** 校验 yyyyMM 期间参数 */
    private String requirePeriod(String period, String field) {
        if (period == null || period.isBlank()) {
            throw BusinessException.ofKey("erp.cost.recalc-param-required", field);
        }
        try {
            YearMonth.parse(period.trim(), CostConstants.PERIOD_FORMATTER);
        } catch (DateTimeParseException e) {
            throw BusinessException.ofKey("erp.cost.recalc-param-invalid", field, period);
        }
        return period.trim();
    }

    /** 下一期间（yyyyMM + 1 月） */
    private String nextPeriod(String period) {
        return YearMonth.parse(period, CostConstants.PERIOD_FORMATTER).plusMonths(1)
                .format(CostConstants.PERIOD_FORMATTER);
    }
}

package com.erp.cost.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.cost.constant.CostBizType;
import com.erp.cost.dto.vo.MaterialBalanceVO;
import com.erp.cost.entity.CostFlow;
import com.erp.cost.service.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CostService 实现 —— 对外计价 SPI，委托 {@link CostLedger} 记账引擎。
 * <p>
 * 与 sync 重算共用 CostLedger 同一路径，保证「重算 == 实时」。
 * 事务边界：每次过账独立事务（rollbackFor Exception），由调用方
 * （采购/销售/生产模块的单据审核事务）自然串成同一事务。
 */
@Service
@RequiredArgsConstructor
public class CostServiceImpl implements CostService {

    private final CostLedger ledger;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onStockIn(Long materialId, String bizType, String bizNo, BigDecimal qty, BigDecimal amount) {
        onStockIn(materialId, null, bizType, bizNo, null, qty, amount, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onStockIn(Long materialId, Long warehouseId, String bizType, String bizNo,
                          Long bizId, BigDecimal qty, BigDecimal amount, LocalDate bizDate) {
        requireMaterial(materialId);
        ledger.postIn(materialId, warehouseId, requireType(bizType), bizNo, bizId, null, qty, amount, bizDate);
    }

    @Override
    public BigDecimal calcOutCost(Long materialId, BigDecimal qty) {
        requireMaterial(materialId);
        if (qty == null || qty.signum() <= 0) {
            throw BusinessException.ofKey("erp.cost.outbound-cost-qty-positive", materialId);
        }
        CostFlow last = ledger.lastRead(materialId);
        // 结存为 0 时退化为最后一次入账单价；无任何成本历史返回 0（新物料未计价场景）
        return ledger.weightedUnit(last).multiply(qty)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal onStockOut(Long materialId, String bizType, String bizNo, BigDecimal qty) {
        return onStockOut(materialId, null, bizType, bizNo, null, qty, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal onStockOut(Long materialId, Long warehouseId, String bizType, String bizNo,
                                 Long bizId, BigDecimal qty, LocalDate bizDate) {
        requireMaterial(materialId);
        CostFlow flow = ledger.postOut(materialId, warehouseId, requireType(bizType), bizNo,
                bizId, null, qty, bizDate);
        return flow.getAmount().negate();
    }

    @Override
    public MaterialBalanceVO currentBalance(Long materialId) {
        requireMaterial(materialId);
        CostFlow last = ledger.lastRead(materialId);
        if (last == null) {
            return new MaterialBalanceVO(materialId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new MaterialBalanceVO(materialId, last.getBalanceQty(), last.getBalanceCost(),
                ledger.weightedUnit(last));
    }

    private void requireMaterial(Long materialId) {
        if (materialId == null) {
            throw BusinessException.ofKey("erp.cost.material-required");
        }
    }

    private CostBizType requireType(String bizType) {
        CostBizType type = CostBizType.ofCode(bizType);
        if (type == null) {
            throw BusinessException.ofKey("erp.cost.unknown-biz-type", bizType);
        }
        return type;
    }
}

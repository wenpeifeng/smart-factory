package com.erp.hr.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.hr.constant.HrConstants;
import com.erp.hr.dto.vo.PieceWageCollectResultVO;
import com.erp.hr.dto.vo.PieceWageDetailVO;
import com.erp.hr.dto.vo.PieceWageEmployeeSummaryVO;
import com.erp.hr.dto.vo.PieceWageVO;
import com.erp.hr.dto.vo.WorkOrderSourceVO;
import com.erp.hr.entity.HrEmployee;
import com.erp.hr.entity.HrPiecePrice;
import com.erp.hr.entity.HrPieceWage;
import com.erp.hr.entity.HrPieceWageDetail;
import com.erp.hr.mapper.HrEmployeeMapper;
import com.erp.hr.mapper.HrIntegrationMapper;
import com.erp.hr.mapper.HrPiecePriceMapper;
import com.erp.hr.mapper.HrPieceWageDetailMapper;
import com.erp.hr.mapper.HrPieceWageMapper;
import com.erp.hr.mapper.HrStatMapper;
import com.erp.hr.service.PieceWageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 计件工资服务实现。
 * <p>
 * 归集为"整单重建"：每次 collect 物理清空本期间旧明细再重建，保证幂等
 * （明细为可再生派生数据，逻辑删除行会占用 uk(period_id, work_order_id)）；
 * 跨期防重：已被其他期间归集的订单 NOT EXISTS 排除（同一完工量只计一次计件）。
 * 结账后（CLOSED）一律拒绝归集。
 */
@Service
@RequiredArgsConstructor
public class PieceWageServiceImpl implements PieceWageService {

    private static final Pattern PERIOD_FMT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final HrPieceWageMapper wageMapper;
    private final HrPieceWageDetailMapper detailMapper;
    private final HrPiecePriceMapper priceMapper;
    private final HrEmployeeMapper employeeMapper;
    private final HrStatMapper statMapper;
    private final HrIntegrationMapper integrationMapper;

    @Override
    public PageResult<PieceWageVO> page(long pageNum, long pageSize) {
        IPage<PieceWageVO> page = statMapper.pagePieceWages(new Page<>(pageNum, pageSize));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PieceWageVO detail(String period) {
        HrPieceWage wage = requirePeriod(period);
        // 复用分页 SQL 的聚合口径：按期间号查首页聚合行
        IPage<PieceWageVO> all = statMapper.pagePieceWages(new Page<>(1, 500));
        return all.getRecords().stream()
                .filter(v -> wage.getId().equals(v.getId()))
                .findFirst()
                .orElseGet(() -> {
                    PieceWageVO vo = new PieceWageVO();
                    vo.setId(wage.getId());
                    vo.setPeriod(wage.getPeriod());
                    vo.setStatus(wage.getStatus());
                    vo.setRemark(wage.getRemark());
                    vo.setCreateTime(wage.getCreateTime());
                    vo.setDetailCount(0L);
                    vo.setTotalAmount(BigDecimal.ZERO);
                    return vo;
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PieceWageCollectResultVO collect(String period) {
        requirePeriodFormat(period);
        if (integrationMapper.tableExists("mrp_work_order") == 0) {
            throw BusinessException.ofKey("erp.hr.mrp-table-not-ready");
        }
        // 期间不存在自动开账；已结账拒绝
        HrPieceWage wage = wageMapper.selectOne(Wrappers.lambdaQuery(HrPieceWage.class)
                .eq(HrPieceWage::getPeriod, period));
        if (wage == null) {
            wage = new HrPieceWage();
            wage.setPeriod(period);
            wage.setStatus(HrConstants.WAGE_OPEN);
            wageMapper.insert(wage);
        } else if (HrConstants.WAGE_CLOSED.equals(wage.getStatus())) {
            throw BusinessException.ofKey("erp.hr.period-closed-no-repeat-collect", period);
        }

        // 幂等：物理清空本期间旧明细后重建（派生数据可再生；逻辑删除行会占用
        // uk(period_id, work_order_id) 导致重建唯一键冲突），跨期明细不受影响
        statMapper.deletePeriodDetails(wage.getId());

        List<WorkOrderSourceVO> orders = integrationMapper.selectFinishedWorkOrders(period, wage.getId());
        // 价目：物料 → 首条启用价目（按 id 升序，视为"首层工序"，一期简化口径）
        Map<Long, HrPiecePrice> priceByMaterial = firstEnabledPrices(
                orders.stream().map(WorkOrderSourceVO::getMaterialId).distinct().toList());
        // 员工：工单制单人(sys_user.id) → hr_employee.user_id，在职优先
        Map<Long, HrEmployee> employeeByUser = employeeByUserId(
                orders.stream().map(WorkOrderSourceVO::getCreateBy).filter(c -> c != null && c > 0)
                        .distinct().toList());

        PieceWageCollectResultVO result = new PieceWageCollectResultVO();
        result.setPeriodId(wage.getId());
        result.setPeriod(period);
        result.setCandidateOrders(orders.size());
        result.setTotalAmount(BigDecimal.ZERO);

        List<HrPieceWageDetail> details = new ArrayList<>();
        for (WorkOrderSourceVO wo : orders) {
            HrPiecePrice price = priceByMaterial.get(wo.getMaterialId());
            if (price == null) {
                result.setSkippedNoPrice(result.getSkippedNoPrice() + 1);
                continue;
            }
            HrEmployee employee = wo.getCreateBy() == null ? null : employeeByUser.get(wo.getCreateBy());
            if (employee == null) {
                result.setSkippedNoEmployee(result.getSkippedNoEmployee() + 1);
                continue;
            }
            // 金额 = 完工数量 × 单价（HALF_UP 2 位，与全系统金额精度一致）
            BigDecimal amount = wo.getFinishQty().multiply(price.getUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            HrPieceWageDetail d = new HrPieceWageDetail();
            d.setPeriodId(wage.getId());
            d.setEmployeeId(employee.getId());
            d.setWorkOrderId(wo.getId());
            d.setWorkOrderNo(wo.getOrderNo());
            d.setMaterialId(wo.getMaterialId());
            d.setProcessName(price.getProcessName());
            d.setQty(wo.getFinishQty());
            d.setUnitPrice(price.getUnitPrice());
            d.setAmount(amount);
            details.add(d);
            result.setTotalAmount(result.getTotalAmount().add(amount));
        }
        // 批量落库（明细量=当期完工订单数，逐条 insert 足够；事务内）
        details.forEach(detailMapper::insert);
        result.setDetailCount(details.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(String period) {
        HrPieceWage wage = requirePeriod(period);
        if (HrConstants.WAGE_CLOSED.equals(wage.getStatus())) {
            throw BusinessException.ofKey("erp.hr.period-closed-no-repeat2", period);
        }
        HrPieceWage upd = new HrPieceWage();
        upd.setId(wage.getId());
        upd.setStatus(HrConstants.WAGE_CLOSED);
        wageMapper.updateById(upd);
    }

    @Override
    public PageResult<PieceWageDetailVO> details(String period, Long employeeId, long pageNum, long pageSize) {
        HrPieceWage wage = requirePeriod(period);
        IPage<PieceWageDetailVO> page = statMapper.pagePieceWageDetails(
                new Page<>(pageNum, pageSize), wage.getId(), employeeId);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PieceWageEmployeeSummaryVO> employeeSummary(String period) {
        HrPieceWage wage = requirePeriod(period);
        return statMapper.pieceWageEmployeeSummary(wage.getId());
    }

    // ==================== 私有工具 ====================

    private HrPieceWage requirePeriod(String period) {
        requirePeriodFormat(period);
        HrPieceWage wage = wageMapper.selectOne(Wrappers.lambdaQuery(HrPieceWage.class)
                .eq(HrPieceWage::getPeriod, period));
        if (wage == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.hr.piece-period-not-exists", period);
        }
        return wage;
    }

    private void requirePeriodFormat(String period) {
        if (period == null || !PERIOD_FMT.matcher(period).matches()) {
            throw BusinessException.ofKey("erp.hr.period-format-invalid", period);
        }
    }

    /** 物料 → 首条启用价目（ORDER BY id，"首层工序"简化口径） */
    private Map<Long, HrPiecePrice> firstEnabledPrices(List<Long> materialIds) {
        Map<Long, HrPiecePrice> map = new HashMap<>();
        if (materialIds.isEmpty()) {
            return map;
        }
        List<HrPiecePrice> prices = priceMapper.selectList(Wrappers.lambdaQuery(HrPiecePrice.class)
                .eq(HrPiecePrice::getStatus, HrConstants.PRICE_ENABLED)
                .in(HrPiecePrice::getMaterialId, materialIds)
                .orderByAsc(HrPiecePrice::getId));
        for (HrPiecePrice p : prices) {
            map.putIfAbsent(p.getMaterialId(), p);
        }
        return map;
    }

    /** 制单人(sys_user.id) → 员工（同账号多员工时在职优先、再按工号稳定取一） */
    private Map<Long, HrEmployee> employeeByUserId(List<Long> userIds) {
        Map<Long, HrEmployee> map = new HashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        List<HrEmployee> employees = employeeMapper.selectList(Wrappers.lambdaQuery(HrEmployee.class)
                .in(HrEmployee::getUserId, userIds)
                .orderByAsc(HrEmployee::getEmpNo));
        for (HrEmployee e : employees) {
            HrEmployee exist = map.get(e.getUserId());
            if (exist == null || (!HrConstants.EMP_ON_JOB.equals(exist.getStatus())
                    && HrConstants.EMP_ON_JOB.equals(e.getStatus()))) {
                map.put(e.getUserId(), e);
            }
        }
        return map;
    }
}

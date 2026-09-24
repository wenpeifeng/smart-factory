package com.erp.equipment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.BaseWarehouse;
import com.erp.basedata.service.BaseMaterialService;
import com.erp.basedata.service.BaseWarehouseService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.equipment.constant.EqpBizType;
import com.erp.equipment.dto.form.RepairAcceptForm;
import com.erp.equipment.dto.form.RepairCompleteForm;
import com.erp.equipment.dto.form.RepairReportForm;
import com.erp.equipment.dto.request.RepairQuery;
import com.erp.equipment.dto.vo.RepairDetailVO;
import com.erp.equipment.dto.vo.RepairVO;
import com.erp.equipment.entity.EqpDevice;
import com.erp.equipment.entity.EqpInvMovement;
import com.erp.equipment.entity.EqpRepair;
import com.erp.equipment.entity.EqpRepairItem;
import com.erp.equipment.mapper.EqpDeviceMapper;
import com.erp.equipment.mapper.EqpInvMovementMapper;
import com.erp.equipment.mapper.EqpInvStockMapper;
import com.erp.equipment.mapper.EqpRepairItemMapper;
import com.erp.equipment.mapper.EqpRepairMapper;
import com.erp.equipment.service.EqpRepairService;
import com.erp.equipment.support.EqpBizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 维修工单服务实现。
 * <p>
 * 状态机（单向）：0 待维修 --接单--> 1 维修中 --完工--> 2 已修复 --关闭--> 3 已关闭。
 * <ul>
 *   <li>接单：设备状态联动置「维修中」；</li>
 *   <li>完工：备件明细逐行出库（条件 UPDATE 扣 inv_stock 防负库存 + INSERT inv_movement
 *       biz_type=EQP_OUT，正入负出），任一行不足抛 BusinessException 整体回滚；
 *       成本参考 = 数量 × base_material.ref_cost（完工时点快照，HALF_UP 2 位）；
 *       设备状态联动回「运行」；</li>
 *   <li>关闭：仅归档，无库存动作。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EqpRepairServiceImpl implements EqpRepairService {

    private final EqpRepairMapper repairMapper;
    private final EqpRepairItemMapper itemMapper;
    private final EqpInvStockMapper invStockMapper;
    private final EqpInvMovementMapper invMovementMapper;
    private final EqpDeviceMapper deviceMapper;
    private final EqpDeviceServiceImpl deviceService;
    private final BaseMaterialService materialService;
    private final BaseWarehouseService warehouseService;
    private final EqpBizNoGenerator bizNoGenerator;

    @Override
    public PageResult<RepairVO> page(RepairQuery query) {
        IPage<RepairVO> page = repairMapper.selectRepairPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        page.getRecords().forEach(vo -> vo.setStatusLabel(statusLabel(vo.getStatus())));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public RepairDetailVO detail(Long id) {
        EqpRepair repair = requireRepair(id);
        EqpDevice device = deviceService.requireDevice(repair.getDeviceId());
        RepairDetailVO vo = new RepairDetailVO();
        vo.setId(repair.getId());
        vo.setRepairNo(repair.getRepairNo());
        vo.setDeviceId(repair.getDeviceId());
        vo.setDeviceCode(device.getCode());
        vo.setDeviceName(device.getName());
        vo.setFaultDesc(repair.getFaultDesc());
        vo.setReporter(repair.getReporter());
        vo.setReportTime(repair.getReportTime());
        vo.setStatus(repair.getStatus());
        vo.setRepairer(repair.getRepairer());
        vo.setFaultReason(repair.getFaultReason());
        vo.setWarehouseId(repair.getWarehouseId());
        vo.setFinishTime(repair.getFinishTime());
        vo.setRemark(repair.getRemark());
        vo.setLines(itemMapper.selectItemsByRepairId(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long report(RepairReportForm form) {
        EqpDevice device = deviceService.requireDevice(form.getDeviceId());
        if (device.getStatus() == EqpDevice.STATUS_SCRAPPED) {
            throw BusinessException.ofKey("erp.equipment.scrapped-no-repair", device.getCode());
        }
        EqpRepair repair = new EqpRepair();
        repair.setRepairNo(bizNoGenerator.next("WX"));
        repair.setDeviceId(form.getDeviceId());
        repair.setFaultDesc(form.getFaultDesc());
        repair.setReporter(form.getReporter());
        repair.setReportTime(LocalDateTime.now());
        repair.setStatus(EqpRepair.STATUS_PENDING);
        repair.setRemark(form.getRemark());
        repairMapper.insert(repair);
        return repair.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, RepairAcceptForm form) {
        EqpRepair repair = requireRepair(id);
        requireStatus(repair, EqpRepair.STATUS_PENDING, "接单");
        repair.setStatus(EqpRepair.STATUS_REPAIRING);
        repair.setRepairer(form.getRepairer());
        repairMapper.updateById(repair);

        patchDeviceStatus(repair.getDeviceId(), EqpDevice.STATUS_REPAIRING);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, RepairCompleteForm form) {
        EqpRepair repair = requireRepair(id);
        requireStatus(repair, EqpRepair.STATUS_REPAIRING, "完工");
        List<RepairCompleteForm.Line> lines = form.getLines() == null ? List.of() : form.getLines();

        if (!lines.isEmpty()) {
            if (form.getWarehouseId() == null) {
                throw BusinessException.ofKey("erp.equipment.workorder-spare-warehouse-required", repair.getRepairNo());
            }
            issueSpareParts(repair, form.getWarehouseId(), lines);
        }

        repair.setStatus(EqpRepair.STATUS_FIXED);
        repair.setFaultReason(form.getFaultReason());
        repair.setWarehouseId(form.getWarehouseId());
        repair.setFinishTime(LocalDateTime.now());
        repairMapper.updateById(repair);

        // 修复完成设备恢复可用：维修中 → 运行
        patchDeviceStatus(repair.getDeviceId(), EqpDevice.STATUS_RUNNING);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        EqpRepair repair = requireRepair(id);
        requireStatus(repair, EqpRepair.STATUS_FIXED, "关闭");
        repair.setStatus(EqpRepair.STATUS_CLOSED);
        repairMapper.updateById(repair);
    }

    // ==================== 备件出库（库存约定表操作） ====================

    /**
     * 备件出库：逐行「INSERT 工单明细 + 条件 UPDATE 扣库存 + INSERT EQP_OUT 流水」。
     * <p>
     * 按库存模块约定（docs/modules/inventory.md）直接操作约定表：
     * <ul>
     *   <li>扣减走条件 UPDATE（行锁）：qty = qty + delta WHERE ... AND qty + delta &gt;= 0，
     *       影响行数 0 一律按「库存不足」抛异常（行不存在与余额不足同语义），整个完工事务回滚；</li>
     *   <li>流水 qty 正入负出（出库为负数），biz_type 用本模块常量 EQP_OUT
     *       （库存模块 InvBizType 枚举暂未收录该值，见 EqpBizType Javadoc 与模块文档）。</li>
     * </ul>
     *
     * @param repair      工单（取 id/单号做流水溯源）
     * @param warehouseId 备件出库仓库
     * @param lines       备件明细行（同物料多行时各自独立出库，语义清晰）
     */
    private void issueSpareParts(EqpRepair repair, Long warehouseId, List<RepairCompleteForm.Line> lines) {
        // 仓库存在性校验（跨模块只调 basedata Service）
        List<BaseWarehouse> warehouses = warehouseService.getByIds(List.of(warehouseId));
        if (warehouses.isEmpty()) {
            throw BusinessException.ofKey("erp.equipment.spare-warehouse-not-exists", warehouseId);
        }

        // 物料批量校验 + 参考成本快照
        Set<Long> materialIds = lines.stream()
                .map(RepairCompleteForm.Line::getMaterialId)
                .collect(Collectors.toSet());
        Map<Long, BaseMaterial> materialMap = materialService.getByIds(materialIds).stream()
                .collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
        for (Long materialId : materialIds) {
            if (!materialMap.containsKey(materialId)) {
                throw BusinessException.ofKey("erp.equipment.spare-material-not-exists", materialId);
            }
        }

        for (RepairCompleteForm.Line line : lines) {
            BaseMaterial material = materialMap.get(line.getMaterialId());
            BigDecimal refCost = material.getRefCost() == null ? BigDecimal.ZERO : material.getRefCost();
            BigDecimal costRef = line.getQty().multiply(refCost).setScale(2, RoundingMode.HALF_UP);

            EqpRepairItem item = new EqpRepairItem();
            item.setRepairId(repair.getId());
            item.setMaterialId(line.getMaterialId());
            item.setQty(line.getQty());
            item.setCostRef(costRef);
            itemMapper.insert(item);

            // 扣库存（负增量）+ EQP_OUT 流水；不足抛异常回滚整个完工事务
            BigDecimal delta = line.getQty().negate();
            int rows = invStockMapper.deductStock(warehouseId, line.getMaterialId(), delta);
            if (rows == 0) {
                throw BusinessException.ofKey("erp.equipment.stock-not-enough-detail", warehouseId, material.getCode(), material.getName(), line.getQty().stripTrailingZeros().toPlainString());
            }
            invMovementMapper.insert(buildMovement(repair, warehouseId, line.getMaterialId(), delta));
        }
    }

    /** 组装 EQP_OUT 库存流水（biz_id/biz_no 溯源到维修工单） */
    private EqpInvMovement buildMovement(EqpRepair repair, Long warehouseId, Long materialId, BigDecimal qty) {
        EqpInvMovement movement = new EqpInvMovement();
        movement.setBizType(EqpBizType.EQP_OUT);
        movement.setBizId(repair.getId());
        movement.setBizNo(repair.getRepairNo());
        movement.setWarehouseId(warehouseId);
        movement.setMaterialId(materialId);
        movement.setQty(qty);
        movement.setRemark("维修工单领用备件");
        return movement;
    }

    // ==================== 私有工具 ====================

    /** 状态前置校验（单向状态机） */
    private void requireStatus(EqpRepair repair, int expected, String action) {
        if (repair.getStatus() != expected) {
            throw BusinessException.ofKey("erp.equipment.workorder-status-not-allowed", repair.getRepairNo(), statusLabel(repair.getStatus()), action, statusLabel(expected));
        }
    }

    /** 设备状态联动（仅维护 status 一列） */
    private void patchDeviceStatus(Long deviceId, int status) {
        EqpDevice patch = new EqpDevice();
        patch.setId(deviceId);
        patch.setStatus(status);
        deviceMapper.updateById(patch);
    }

    /** 工单状态码 → 中文 */
    private String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case EqpRepair.STATUS_PENDING -> "待维修";
            case EqpRepair.STATUS_REPAIRING -> "维修中";
            case EqpRepair.STATUS_FIXED -> "已修复";
            case EqpRepair.STATUS_CLOSED -> "已关闭";
            default -> "未知";
        };
    }

    private EqpRepair requireRepair(Long id) {
        EqpRepair repair = repairMapper.selectById(id);
        if (repair == null) {
            throw BusinessException.ofKey("erp.equipment.repair-order-not-exists", id);
        }
        return repair;
    }
}

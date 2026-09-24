package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.PriceRecordForm;
import com.erp.basedata.dto.query.PriceSourceRow;
import com.erp.basedata.dto.vo.PriceCompareRowVO;
import com.erp.basedata.dto.vo.PriceRecordVO;
import com.erp.basedata.dto.vo.PriceRefVO;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.BasePriceContract;
import com.erp.basedata.entity.BaseSupplier;
import com.erp.basedata.entity.PurPriceRecord;
import com.erp.basedata.mapper.BasePriceContractMapper;
import com.erp.basedata.mapper.BaseSupplierMapper;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.mapper.PriceSourceMapper;
import com.erp.basedata.mapper.PurPriceRecordMapper;
import com.erp.basedata.service.BasePriceContractService;
import com.erp.basedata.service.PurPriceService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购价格库 Service 实现。
 * <p>
 * 价格口径：流水一律存<b>不含税</b>单价；订单/入库来源的含税价按
 * price / (1 + taxRate/100) 换算（HALF_UP 4 位），RFQ 定标价直接入库。
 * 扫描器 {@link #collectOnce()} 以 NOT EXISTS 预筛 + uk 兜底，天然幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurPriceServiceImpl implements PurPriceService {

    /** 扫描器单类单轮上限（多轮调度自然消化积压） */
    private static final int SCAN_BATCH = 500;
    /** 历史价页大小上限 */
    private static final int HISTORY_MAX_SIZE = 50;

    private final PurPriceRecordMapper recordMapper;
    private final PriceSourceMapper sourceMapper;
    private final BasePriceContractMapper contractMapper;
    private final BasePriceContractService contractService;
    private final BaseSupplierMapper supplierMapper;
    private final BaseMaterialMapper materialMapper;

    @Override
    public PageResult<PriceRecordVO> page(long pageNum, long pageSize, Long supplierId, Long materialId, String sourceType) {
        var wrapper = Wrappers.lambdaQuery(PurPriceRecord.class)
                .eq(supplierId != null, PurPriceRecord::getSupplierId, supplierId)
                .eq(materialId != null, PurPriceRecord::getMaterialId, materialId)
                .eq(notBlank(sourceType), PurPriceRecord::getSourceType, sourceType)
                .orderByDesc(PurPriceRecord::getPriceDate)
                .orderByDesc(PurPriceRecord::getId);
        PageResult<PriceRecordVO> page = PageUtils.of(recordMapper, wrapper, pageNum, pageSize, PriceRecordVO::from);
        fillNames(page.getRecords());
        return page;
    }

    @Override
    public PriceRefVO lastRef(Long supplierId, Long materialId) {
        PriceRefVO vo = new PriceRefVO();
        vo.setSupplierId(supplierId);
        vo.setMaterialId(materialId);
        // 合同价优先：status=1 且今天落在 [from,to] 窗口
        BasePriceContract contract = contractService.findEffective(supplierId, materialId);
        if (contract != null) {
            PriceRefVO.ContractItem item = new PriceRefVO.ContractItem();
            item.setId(contract.getId());
            item.setPrice(contract.getContractPrice());
            item.setEffectiveFrom(contract.getEffectiveFrom());
            item.setEffectiveTo(contract.getEffectiveTo());
            item.setRemark(contract.getRemark());
            vo.setContract(item);
        }
        // 最近一次成交价（日期倒序，同日取大 ID）
        PurPriceRecord last = recordMapper.selectOne(Wrappers.lambdaQuery(PurPriceRecord.class)
                .eq(PurPriceRecord::getSupplierId, supplierId)
                .eq(PurPriceRecord::getMaterialId, materialId)
                .orderByDesc(PurPriceRecord::getPriceDate)
                .orderByDesc(PurPriceRecord::getId)
                .last("LIMIT 1"));
        if (last != null) {
            PriceRefVO.LastItem item = new PriceRefVO.LastItem();
            item.setId(last.getId());
            item.setPrice(last.getPrice());
            item.setSourceType(last.getSourceType());
            item.setSourceTypeLabel(PriceRecordVO.labelOf(last.getSourceType()));
            item.setSourceNo(last.getSourceNo());
            item.setPriceDate(last.getPriceDate());
            vo.setLast(item);
        }
        return vo;
    }

    @Override
    public PageResult<PriceRecordVO> history(Long materialId, Long supplierId, long pageNum, long pageSize) {
        return page(pageNum, Math.min(pageSize, HISTORY_MAX_SIZE), supplierId, materialId, null);
    }

    @Override
    public List<PriceCompareRowVO> compare(Long materialId) {
        List<PriceCompareRowVO> rows = recordMapper.compareByMaterial(materialId);
        if (rows.isEmpty()) {
            return rows;
        }
        // 供应商名称
        Map<Long, String> supplierNames = supplierMapper.selectBatchIds(
                        rows.stream().map(PriceCompareRowVO::getSupplierId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseSupplier::getId, BaseSupplier::getName, (a, b) -> a));
        // 各供应商生效合同价（对比页同时展示合同口径）
        Map<Long, BasePriceContract> contracts = contractMapper.selectList(Wrappers.lambdaQuery(BasePriceContract.class)
                        .eq(BasePriceContract::getMaterialId, materialId)
                        .eq(BasePriceContract::getStatus, 1)
                        .le(BasePriceContract::getEffectiveFrom, LocalDate.now())
                        .ge(BasePriceContract::getEffectiveTo, LocalDate.now()))
                .stream().collect(Collectors.toMap(BasePriceContract::getSupplierId, Function.identity(),
                        (a, b) -> a.getId() > b.getId() ? a : b));
        for (PriceCompareRowVO row : rows) {
            row.setSupplierName(supplierNames.getOrDefault(row.getSupplierId(), ""));
            BasePriceContract contract = contracts.get(row.getSupplierId());
            if (contract != null) {
                row.setContractPrice(contract.getContractPrice());
            }
        }
        return rows;
    }

    @Override
    public Long createManual(PriceRecordForm form) {
        if (supplierMapper.selectById(form.getSupplierId()) == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.supplier-not-exists", form.getSupplierId());
        }
        if (materialMapper.selectById(form.getMaterialId()) == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.material-not-exists", form.getMaterialId());
        }
        PurPriceRecord record = new PurPriceRecord();
        record.setSupplierId(form.getSupplierId());
        record.setMaterialId(form.getMaterialId());
        record.setPrice(form.getPrice().setScale(4, RoundingMode.HALF_UP));
        record.setSourceType(PurPriceRecord.SOURCE_CONTRACT);
        record.setSourceNo(form.getSourceNo() == null || form.getSourceNo().isBlank()
                ? "手工录入" : form.getSourceNo().trim());
        record.setSourceDetailId(null);
        record.setPriceDate(form.getPriceDate());
        record.setRemark(form.getRemark());
        recordMapper.insert(record);
        return record.getId();
    }

    @Override
    public void delete(Long id) {
        if (recordMapper.selectById(id) == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.price-log-not-exists", id);
        }
        // 逻辑删除后扫描器不会再沉淀该来源行（NOT EXISTS 对物理表判断）
        recordMapper.deleteById(id);
    }

    @Override
    public int collectOnce() {
        int total = 0;
        total += insertBatch(PurPriceRecord.SOURCE_ORDER, sourceMapper.scanOrderRows(SCAN_BATCH));
        total += insertBatch(PurPriceRecord.SOURCE_INSTOCK, sourceMapper.scanInstockRows(SCAN_BATCH));
        total += insertBatch(PurPriceRecord.SOURCE_RFQ, sourceMapper.scanRfqRows(SCAN_BATCH));
        if (total > 0) {
            log.info("[价格库][扫描器] 本轮沉淀采购价格流水 {} 条", total);
        }
        return total;
    }

    /** 单类来源批量沉淀；uk 冲突（多实例并发）跳过该行 */
    private int insertBatch(String sourceType, List<PriceSourceRow> rows) {
        int inserted = 0;
        for (PriceSourceRow row : rows) {
            if (row.getTaxPrice() == null || row.getSupplierId() == null || row.getMaterialId() == null) {
                continue;
            }
            PurPriceRecord record = new PurPriceRecord();
            record.setSupplierId(row.getSupplierId());
            record.setMaterialId(row.getMaterialId());
            record.setPrice(toPriceExclTax(row.getTaxPrice(), row.getTaxRate()));
            record.setSourceType(sourceType);
            record.setSourceNo(row.getSourceNo());
            record.setSourceDetailId(row.getSourceDetailId());
            record.setPriceDate(row.getPriceDate() == null ? LocalDate.now() : row.getPriceDate());
            try {
                recordMapper.insert(record);
                inserted++;
            } catch (DuplicateKeyException e) {
                // 多实例并发兜底：该来源行已被沉淀
            }
        }
        return inserted;
    }

    /** 含税价 → 不含税（税率 NULL/0 视为本身即不含税），HALF_UP 4 位 */
    private BigDecimal toPriceExclTax(BigDecimal taxPrice, BigDecimal taxRate) {
        if (taxRate == null || taxRate.signum() == 0) {
            return taxPrice.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        return taxPrice.divide(factor, 4, RoundingMode.HALF_UP);
    }

    /** 批量填充供应商/物料冗余名称（一次 in 查询，避免 N+1） */
    private void fillNames(List<PriceRecordVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<Long, BaseSupplier> suppliers = supplierMapper.selectBatchIds(
                        records.stream().map(PriceRecordVO::getSupplierId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseSupplier::getId, Function.identity(), (a, b) -> a));
        Map<Long, BaseMaterial> materials = materialMapper.selectBatchIds(
                        records.stream().map(PriceRecordVO::getMaterialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity(), (a, b) -> a));
        for (PriceRecordVO vo : records) {
            BaseSupplier supplier = suppliers.get(vo.getSupplierId());
            if (supplier != null) {
                vo.setSupplierName(supplier.getName());
            }
            BaseMaterial material = materials.get(vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getCode());
                vo.setMaterialName(material.getName());
            }
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

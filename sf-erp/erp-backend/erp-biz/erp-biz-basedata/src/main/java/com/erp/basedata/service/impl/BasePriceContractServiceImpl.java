package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.PriceContractForm;
import com.erp.basedata.dto.vo.PriceContractVO;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.BasePriceContract;
import com.erp.basedata.entity.BaseSupplier;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.mapper.BasePriceContractMapper;
import com.erp.basedata.mapper.BaseSupplierMapper;
import com.erp.basedata.service.BasePriceContractService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购合同价 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class BasePriceContractServiceImpl implements BasePriceContractService {

    private final BasePriceContractMapper contractMapper;
    private final BaseSupplierMapper supplierMapper;
    private final BaseMaterialMapper materialMapper;

    @Override
    public PageResult<PriceContractVO> page(long pageNum, long pageSize, Long supplierId, Long materialId, Integer status) {
        var wrapper = Wrappers.lambdaQuery(BasePriceContract.class)
                .eq(supplierId != null, BasePriceContract::getSupplierId, supplierId)
                .eq(materialId != null, BasePriceContract::getMaterialId, materialId)
                .eq(status != null, BasePriceContract::getStatus, status)
                .orderByDesc(BasePriceContract::getEffectiveTo)
                .orderByDesc(BasePriceContract::getId);
        PageResult<PriceContractVO> page = PageUtils.of(contractMapper, wrapper, pageNum, pageSize, PriceContractVO::from);
        fillNames(page.getRecords());
        return page;
    }

    @Override
    public BasePriceContract findEffective(Long supplierId, Long materialId) {
        LocalDate today = LocalDate.now();
        // 生效窗口重叠时取最新一条（同参照口径唯一，避免歧义）
        return contractMapper.selectOne(Wrappers.lambdaQuery(BasePriceContract.class)
                .eq(BasePriceContract::getSupplierId, supplierId)
                .eq(BasePriceContract::getMaterialId, materialId)
                .eq(BasePriceContract::getStatus, 1)
                .le(BasePriceContract::getEffectiveFrom, today)
                .ge(BasePriceContract::getEffectiveTo, today)
                .orderByDesc(BasePriceContract::getId)
                .last("LIMIT 1"));
    }

    @Override
    public Long create(PriceContractForm form) {
        validate(form);
        BasePriceContract entity = new BasePriceContract();
        applyForm(entity, form);
        contractMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, PriceContractForm form) {
        validate(form);
        BasePriceContract entity = requireExists(id);
        applyForm(entity, form);
        contractMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        contractMapper.deleteById(id);
    }

    /** 表单校验：供应商/物料存在性 + 生效期顺序 */
    private void validate(PriceContractForm form) {
        if (supplierMapper.selectById(form.getSupplierId()) == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.supplier-not-exists", form.getSupplierId());
        }
        if (materialMapper.selectById(form.getMaterialId()) == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.material-not-exists", form.getMaterialId());
        }
        if (form.getEffectiveTo().isBefore(form.getEffectiveFrom())) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.basedata.effective-end-before-start");
        }
    }

    private void applyForm(BasePriceContract entity, PriceContractForm form) {
        entity.setSupplierId(form.getSupplierId());
        entity.setMaterialId(form.getMaterialId());
        entity.setContractPrice(form.getContractPrice());
        entity.setEffectiveFrom(form.getEffectiveFrom());
        entity.setEffectiveTo(form.getEffectiveTo());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 批量填充供应商/物料冗余名称 */
    private void fillNames(java.util.List<PriceContractVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Map<Long, BaseSupplier> suppliers = supplierMapper.selectBatchIds(
                        records.stream().map(PriceContractVO::getSupplierId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseSupplier::getId, Function.identity(), (a, b) -> a));
        Map<Long, BaseMaterial> materials = materialMapper.selectBatchIds(
                        records.stream().map(PriceContractVO::getMaterialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity(), (a, b) -> a));
        for (PriceContractVO vo : records) {
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

    private BasePriceContract requireExists(Long id) {
        BasePriceContract entity = contractMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.contract-price-not-exists", id);
        }
        return entity;
    }
}

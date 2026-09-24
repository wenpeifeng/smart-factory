package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.PriceLevelForm;
import com.erp.basedata.dto.form.PriceQtyForm;
import com.erp.basedata.dto.vo.PriceLevelVO;
import com.erp.basedata.dto.vo.PriceQtyVO;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.SalPriceLevel;
import com.erp.basedata.entity.SalPriceQty;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.mapper.SalPriceLevelMapper;
import com.erp.basedata.mapper.SalPriceQtyMapper;
import com.erp.basedata.service.SalPriceStrategyService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售价格策略 Service 实现（等级价 + 批量价）。
 * <p>
 * uk 冲突（物料+等级 / 物料+起订量重复）统一转 DUPLICATE_DATA 业务提示。
 */
@Service
@RequiredArgsConstructor
public class SalPriceStrategyServiceImpl implements SalPriceStrategyService {

    private final SalPriceLevelMapper levelMapper;
    private final SalPriceQtyMapper qtyMapper;
    private final BaseMaterialMapper materialMapper;

    /* ===================== 等级价 ===================== */

    @Override
    public PageResult<PriceLevelVO> pageLevels(long pageNum, long pageSize, Long materialId, String level, Integer status) {
        var wrapper = Wrappers.lambdaQuery(SalPriceLevel.class)
                .eq(materialId != null, SalPriceLevel::getMaterialId, materialId)
                .eq(level != null && !level.isBlank(), SalPriceLevel::getLevel, level)
                .eq(status != null, SalPriceLevel::getStatus, status)
                .orderByAsc(SalPriceLevel::getMaterialId)
                .orderByAsc(SalPriceLevel::getLevel);
        PageResult<PriceLevelVO> page = PageUtils.of(levelMapper, wrapper, pageNum, pageSize, PriceLevelVO::from);
        fillLevelNames(page.getRecords());
        return page;
    }

    @Override
    public SalPriceLevel findEnabledLevel(Long materialId, String level) {
        return levelMapper.selectOne(Wrappers.lambdaQuery(SalPriceLevel.class)
                .eq(SalPriceLevel::getMaterialId, materialId)
                .eq(SalPriceLevel::getLevel, level)
                .eq(SalPriceLevel::getStatus, 1)
                .orderByDesc(SalPriceLevel::getId)
                .last("LIMIT 1"));
    }

    @Override
    public Long createLevel(PriceLevelForm form) {
        checkMaterialExists(form.getMaterialId());
        checkLevelUnique(form.getMaterialId(), form.getLevel(), null);
        SalPriceLevel entity = new SalPriceLevel();
        applyLevelForm(entity, form);
        try {
            levelMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw duplicateLevel(form.getMaterialId(), form.getLevel());
        }
        return entity.getId();
    }

    @Override
    public void updateLevel(Long id, PriceLevelForm form) {
        checkMaterialExists(form.getMaterialId());
        SalPriceLevel entity = requireLevel(id);
        checkLevelUnique(form.getMaterialId(), form.getLevel(), id);
        applyLevelForm(entity, form);
        try {
            levelMapper.updateById(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw duplicateLevel(form.getMaterialId(), form.getLevel());
        }
    }

    @Override
    public void deleteLevel(Long id) {
        requireLevel(id);
        levelMapper.deleteById(id);
    }

    /* ===================== 批量价 ===================== */

    @Override
    public PageResult<PriceQtyVO> pageQtys(long pageNum, long pageSize, Long materialId, Integer status) {
        var wrapper = Wrappers.lambdaQuery(SalPriceQty.class)
                .eq(materialId != null, SalPriceQty::getMaterialId, materialId)
                .eq(status != null, SalPriceQty::getStatus, status)
                .orderByAsc(SalPriceQty::getMaterialId)
                .orderByAsc(SalPriceQty::getMinQty);
        PageResult<PriceQtyVO> page = PageUtils.of(qtyMapper, wrapper, pageNum, pageSize, PriceQtyVO::from);
        fillQtyNames(page.getRecords());
        return page;
    }

    @Override
    public SalPriceQty matchQtyTier(Long materialId, BigDecimal qty) {
        BigDecimal safeQty = qty == null ? BigDecimal.ZERO : qty;
        // 满足 qty 的最高 min_qty 档 = 满足数量的最低价格档（阶梯约定 min_qty 越大价越低）
        return qtyMapper.selectOne(Wrappers.lambdaQuery(SalPriceQty.class)
                .eq(SalPriceQty::getMaterialId, materialId)
                .eq(SalPriceQty::getStatus, 1)
                .le(SalPriceQty::getMinQty, safeQty)
                .orderByDesc(SalPriceQty::getMinQty)
                .last("LIMIT 1"));
    }

    @Override
    public Long createQty(PriceQtyForm form) {
        checkMaterialExists(form.getMaterialId());
        checkQtyUnique(form.getMaterialId(), form.getMinQty(), null);
        SalPriceQty entity = new SalPriceQty();
        applyQtyForm(entity, form);
        try {
            qtyMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw duplicateQty(form.getMaterialId(), form.getMinQty());
        }
        return entity.getId();
    }

    @Override
    public void updateQty(Long id, PriceQtyForm form) {
        checkMaterialExists(form.getMaterialId());
        SalPriceQty entity = requireQty(id);
        checkQtyUnique(form.getMaterialId(), form.getMinQty(), id);
        applyQtyForm(entity, form);
        try {
            qtyMapper.updateById(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw duplicateQty(form.getMaterialId(), form.getMinQty());
        }
    }

    @Override
    public void deleteQty(Long id) {
        requireQty(id);
        qtyMapper.deleteById(id);
    }

    /* ===================== 私有支撑 ===================== */

    private void checkMaterialExists(Long materialId) {
        if (materialMapper.selectById(materialId) == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.material-not-exists", materialId);
        }
    }

    private void checkLevelUnique(Long materialId, String level, Long excludeId) {
        Long count = levelMapper.selectCount(Wrappers.lambdaQuery(SalPriceLevel.class)
                .eq(SalPriceLevel::getMaterialId, materialId)
                .eq(SalPriceLevel::getLevel, level)
                .ne(excludeId != null, SalPriceLevel::getId, excludeId));
        if (count != null && count > 0) {
            throw duplicateLevel(materialId, level);
        }
    }

    private void checkQtyUnique(Long materialId, BigDecimal minQty, Long excludeId) {
        Long count = qtyMapper.selectCount(Wrappers.lambdaQuery(SalPriceQty.class)
                .eq(SalPriceQty::getMaterialId, materialId)
                .eq(SalPriceQty::getMinQty, minQty)
                .ne(excludeId != null, SalPriceQty::getId, excludeId));
        if (count != null && count > 0) {
            throw duplicateQty(materialId, minQty);
        }
    }

    private BusinessException duplicateLevel(Long materialId, String level) {
        return BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.basedata.grade-price-exists", level, materialId);
    }

    private BusinessException duplicateQty(Long materialId, BigDecimal minQty) {
        return BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.basedata.batch-price-exists", minQty.stripTrailingZeros().toPlainString(), materialId);
    }

    private void applyLevelForm(SalPriceLevel entity, PriceLevelForm form) {
        entity.setMaterialId(form.getMaterialId());
        entity.setLevel(form.getLevel().trim().toUpperCase());
        entity.setPrice(form.getPrice());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    private void applyQtyForm(SalPriceQty entity, PriceQtyForm form) {
        entity.setMaterialId(form.getMaterialId());
        entity.setMinQty(form.getMinQty());
        entity.setPrice(form.getPrice());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    private void fillQtyNames(List<PriceQtyVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Map<Long, BaseMaterial> materials = materialMapper.selectBatchIds(
                        records.stream().map(PriceQtyVO::getMaterialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity(), (a, b) -> a));
        for (PriceQtyVO vo : records) {
            BaseMaterial material = materials.get(vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getCode());
                vo.setMaterialName(material.getName());
            }
        }
    }

    /** 等级价分页填充物料冗余编码/名称 */
    private void fillLevelNames(List<PriceLevelVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Map<Long, BaseMaterial> materials = materialMapper.selectBatchIds(
                        records.stream().map(PriceLevelVO::getMaterialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity(), (a, b) -> a));
        for (PriceLevelVO vo : records) {
            BaseMaterial material = materials.get(vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getCode());
                vo.setMaterialName(material.getName());
            }
        }
    }

    private SalPriceLevel requireLevel(Long id) {
        SalPriceLevel entity = levelMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.grade-price-not-exists", id);
        }
        return entity;
    }

    private SalPriceQty requireQty(Long id) {
        SalPriceQty entity = qtyMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.batch-price-not-exists", id);
        }
        return entity;
    }
}

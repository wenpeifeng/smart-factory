package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.i18n.MessageKeys;
import com.erp.common.result.ErrorCode;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.dto.form.BomSubstituteBatchForm;
import com.erp.mrp.dto.form.BomSubstituteForm;
import com.erp.mrp.dto.vo.BomSubstituteVO;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.entity.MrpBom;
import com.erp.mrp.entity.MrpBomItem;
import com.erp.mrp.entity.MrpBomSubstitute;
import com.erp.mrp.mapper.MrpBomItemMapper;
import com.erp.mrp.mapper.MrpBomSubstituteMapper;
import com.erp.mrp.mapper.MrpBomMapper;
import com.erp.mrp.service.MrpBomSubstituteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BOM 子件替代组服务实现（V621）。
 * <p>
 * 约束：替代料不能与主料相同、同子件行内替代料不重复；
 * 仅 DRAFT 状态 BOM 允许维护（BOM 明细重建时会级联清理替代组，见 MrpBomServiceImpl）。
 */
@Service
@RequiredArgsConstructor
public class MrpBomSubstituteServiceImpl implements MrpBomSubstituteService {

    private final MrpBomSubstituteMapper substituteMapper;
    private final MrpBomItemMapper bomItemMapper;
    private final MrpBomMapper bomMapper;
    private final MrpLookupHelper lookup;

    @Override
    public List<BomSubstituteVO> listByItem(Long bomItemId) {
        MrpBomItem item = requireItem(bomItemId);
        return toVOs(listRows(bomItemId), item);
    }

    @Override
    public Map<Long, List<BomSubstituteVO>> listByBom(Long bomId) {
        List<MrpBomItem> items = bomItemMapper.selectList(
                Wrappers.lambdaQuery(MrpBomItem.class).eq(MrpBomItem::getBomId, bomId));
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> itemIds = items.stream().map(MrpBomItem::getId).toList();
        List<MrpBomSubstitute> rows = substituteMapper.selectList(
                Wrappers.lambdaQuery(MrpBomSubstitute.class)
                        .in(MrpBomSubstitute::getBomItemId, itemIds)
                        .orderByAsc(MrpBomSubstitute::getPriority)
                        .orderByAsc(MrpBomSubstitute::getId));
        Map<Long, List<BomSubstituteVO>> result = new HashMap<>();
        for (MrpBomItem item : items) {
            result.put(item.getId(), toVOs(rows.stream()
                    .filter(r -> r.getBomItemId().equals(item.getId())).toList(), item));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(Long bomItemId, BomSubstituteBatchForm form) {
        MrpBomItem item = requireItem(bomItemId);
        requireDraftBom(item.getBomId());
        List<BomSubstituteForm> items = form.getItems() == null ? List.of() : form.getItems();
        // 校验：替代料存在、不等于主料、不重复
        Set<Long> subIds = new HashSet<>();
        for (BomSubstituteForm f : items) {
            if (f.getSubMaterialId().equals(item.getMaterialId())) {
                throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, MessageKeys.MRP_SUB_SAME_AS_MAIN);
            }
            if (!subIds.add(f.getSubMaterialId())) {
                throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, MessageKeys.MRP_SUB_DUPLICATE);
            }
        }
        if (!subIds.isEmpty() && lookup.materialMap(List.copyOf(subIds)).size() != subIds.size()) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.substitute-material-not-exists");
        }
        // 整组重建
        substituteMapper.delete(Wrappers.lambdaQuery(MrpBomSubstitute.class)
                .eq(MrpBomSubstitute::getBomItemId, bomItemId));
        for (BomSubstituteForm f : items) {
            MrpBomSubstitute row = new MrpBomSubstitute();
            row.setBomItemId(bomItemId);
            row.setSubMaterialId(f.getSubMaterialId());
            row.setPriority(f.getPriority());
            row.setRatio(f.getRatio());
            row.setRemark(f.getRemark());
            substituteMapper.insert(row);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MrpBomSubstitute row = substituteMapper.selectById(id);
        if (row == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.substitute-not-exists", id);
        }
        MrpBomItem item = requireItem(row.getBomItemId());
        requireDraftBom(item.getBomId());
        substituteMapper.deleteById(id);
    }

    // ==================== 私有工具 ====================

    private List<MrpBomSubstitute> listRows(Long bomItemId) {
        return substituteMapper.selectList(Wrappers.lambdaQuery(MrpBomSubstitute.class)
                .eq(MrpBomSubstitute::getBomItemId, bomItemId)
                .orderByAsc(MrpBomSubstitute::getPriority)
                .orderByAsc(MrpBomSubstitute::getId));
    }

    private MrpBomItem requireItem(Long bomItemId) {
        MrpBomItem item = bomItemMapper.selectById(bomItemId);
        if (item == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.bom-line-not-exists", bomItemId);
        }
        return item;
    }

    private void requireDraftBom(Long bomId) {
        MrpBom bom = bomMapper.selectById(bomId);
        if (bom == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.bom-not-exists", bomId);
        }
        if (!MrpConstants.BOM_DRAFT.equals(bom.getStatus())) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, MessageKeys.MRP_BOM_NOT_DRAFT_SUB);
        }
    }

    private List<BomSubstituteVO> toVOs(List<MrpBomSubstitute> rows, MrpBomItem item) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> materialIds = new HashSet<>();
        materialIds.add(item.getMaterialId());
        rows.forEach(r -> materialIds.add(r.getSubMaterialId()));
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(List.copyOf(materialIds));
        MaterialBriefVO main = materials.get(item.getMaterialId());
        List<BomSubstituteVO> vos = new ArrayList<>(rows.size());
        for (MrpBomSubstitute row : rows) {
            BomSubstituteVO vo = new BomSubstituteVO();
            vo.setId(row.getId());
            vo.setBomItemId(row.getBomItemId());
            vo.setBomId(item.getBomId());
            vo.setMainMaterialId(item.getMaterialId());
            if (main != null) {
                vo.setMainMaterialCode(main.getCode());
                vo.setMainMaterialName(main.getName());
            }
            vo.setSubMaterialId(row.getSubMaterialId());
            MaterialBriefVO sub = materials.get(row.getSubMaterialId());
            if (sub != null) {
                vo.setSubMaterialCode(sub.getCode());
                vo.setSubMaterialName(sub.getName());
                vo.setUnit(sub.getUnit());
            }
            vo.setPriority(row.getPriority());
            vo.setRatio(row.getRatio());
            vo.setRemark(row.getRemark());
            vos.add(vo);
        }
        return vos;
    }
}

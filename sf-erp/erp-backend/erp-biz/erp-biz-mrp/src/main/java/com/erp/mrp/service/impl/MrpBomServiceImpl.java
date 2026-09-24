package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.dto.form.BomCopyForm;
import com.erp.mrp.dto.form.BomForm;
import com.erp.mrp.dto.form.BomItemForm;
import com.erp.mrp.dto.vo.BomItemVO;
import com.erp.mrp.dto.vo.BomTreeVO;
import com.erp.mrp.dto.vo.BomVO;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.entity.MrpBom;
import com.erp.mrp.entity.MrpBomItem;
import com.erp.mrp.entity.MrpWorkOrder;
import com.erp.mrp.entity.MrpBomSubstitute;
import com.erp.mrp.mapper.MrpBomItemMapper;
import com.erp.mrp.mapper.MrpBomSubstituteMapper;
import com.erp.mrp.mapper.MrpBomMapper;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import com.erp.mrp.mapper.MrpWorkOrderMapper;
import com.erp.mrp.service.MrpBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BOM 服务实现。
 * <p>
 * 关键规则：
 * <ul>
 *   <li>同一成品物料下版本号唯一（服务层校验）；</li>
 *   <li>仅 DRAFT 可编辑/删除；删除前校验未被生产订单引用；</li>
 *   <li>树形展开：根层用指定 BOM 自身，下层按“子件最新有效 BOM”递归，
 *       深度上限 {@link MrpConstants#MAX_BOM_LEVEL}，祖先链出现重复物料即抛循环引用异常。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MrpBomServiceImpl implements MrpBomService {

    private final MrpBomMapper bomMapper;
    private final MrpBomItemMapper bomItemMapper;
    private final MrpBomSubstituteMapper substituteMapper;
    private final MrpWorkOrderMapper workOrderMapper;
    private final MrpIntegrationMapper integrationMapper;
    private final MrpLookupHelper lookup;

    @Override
    public PageResult<BomVO> page(long pageNum, long pageSize, String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        List<Long> materialIds = hasKeyword ? integrationMapper.selectMaterialIds(keyword) : List.of();
        if (hasKeyword && materialIds.isEmpty()) {
            return PageResult.of(List.of(), 0, pageNum, pageSize);
        }
        var wrapper = Wrappers.lambdaQuery(MrpBom.class)
                .eq(status != null && !status.isBlank(), MrpBom::getStatus, status)
                .in(hasKeyword, MrpBom::getMaterialId, materialIds)
                .orderByDesc(MrpBom::getId);
        PageResult<MrpBom> page = PageUtils.of(bomMapper, wrapper, pageNum, pageSize);
        // 批量回显：子件数 + 物料信息
        Map<Long, Long> itemCount = countItems(
                page.getRecords().stream().map(MrpBom::getId).toList());
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                page.getRecords().stream().map(MrpBom::getMaterialId).toList());
        return page.map(bom -> toVO(bom, itemCount.getOrDefault(bom.getId(), 0L), materials));
    }

    @Override
    public BomVO detail(Long id) {
        MrpBom bom = requireBom(id);
        List<MrpBomItem> items = listItems(id);
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                items.stream().map(MrpBomItem::getMaterialId).toList());
        BomVO vo = toVO(bom, (long) items.size(),
                lookup.materialMap(List.of(bom.getMaterialId())));
        vo.setItems(items.stream()
                .map(i -> BomItemVO.of(i.getId(), i.getMaterialId(), i.getQty(), i.getLossRate(),
                        i.getRemark(), materials.get(i.getMaterialId())))
                .toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(BomForm form) {
        validateForm(null, form);
        MrpBom bom = new MrpBom();
        bom.setMaterialId(form.getMaterialId());
        bom.setVersion(form.getVersion());
        bom.setStatus(MrpConstants.BOM_DRAFT);
        bom.setRemark(form.getRemark());
        bomMapper.insert(bom);
        insertItems(bom.getId(), form);
        return bom.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BomForm form) {
        MrpBom bom = requireBom(id);
        if (!MrpConstants.BOM_DRAFT.equals(bom.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.bom-only-draft-modify");
        }
        validateForm(id, form);
        bom.setMaterialId(form.getMaterialId());
        bom.setVersion(form.getVersion());
        bom.setRemark(form.getRemark());
        bomMapper.updateById(bom);
        // 明细整体重建（旧明细及其替代组逻辑删除，V621）
        deleteItemsWithSubstitutes(id);
        insertItems(id, form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MrpBom bom = requireBom(id);
        if (MrpConstants.BOM_ACTIVE.equals(bom.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.bom-audited-no-delete");
        }
        Long referenced = workOrderMapper.selectCount(
                Wrappers.lambdaQuery(MrpWorkOrder.class).eq(MrpWorkOrder::getBomId, id));
        if (referenced != null && referenced > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.mrp.bom-referenced-no-delete");
        }
        bomMapper.deleteById(id);
        deleteItemsWithSubstitutes(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id, BomCopyForm form) {
        MrpBom source = requireBom(id);
        if (versionExists(source.getMaterialId(), form.getNewVersion(), null)) {
            throw BusinessException.ofKey("erp.mrp.bom-version-exists", form.getNewVersion());
        }
        MrpBom target = new MrpBom();
        target.setMaterialId(source.getMaterialId());
        target.setVersion(form.getNewVersion());
        target.setStatus(MrpConstants.BOM_DRAFT);
        target.setRemark(source.getRemark());
        bomMapper.insert(target);
        for (MrpBomItem item : listItems(id)) {
            MrpBomItem copy = new MrpBomItem();
            copy.setBomId(target.getId());
            copy.setMaterialId(item.getMaterialId());
            copy.setQty(item.getQty());
            copy.setLossRate(item.getLossRate());
            copy.setRemark(item.getRemark());
            bomItemMapper.insert(copy);
            // 替代组随行复制（V621）
            for (MrpBomSubstitute sub : substituteMapper.selectList(
                    Wrappers.lambdaQuery(MrpBomSubstitute.class)
                            .eq(MrpBomSubstitute::getBomItemId, item.getId()))) {
                MrpBomSubstitute subCopy = new MrpBomSubstitute();
                subCopy.setBomItemId(copy.getId());
                subCopy.setSubMaterialId(sub.getSubMaterialId());
                subCopy.setPriority(sub.getPriority());
                subCopy.setRatio(sub.getRatio());
                subCopy.setRemark(sub.getRemark());
                substituteMapper.insert(subCopy);
            }
        }
        return target.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        MrpBom bom = requireBom(id);
        if (!MrpConstants.BOM_DRAFT.equals(bom.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.bom-only-draft-audit");
        }
        if (listItems(id).isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.bom-no-components-no-audit");
        }
        bom.setStatus(MrpConstants.BOM_ACTIVE);
        bomMapper.updateById(bom);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        MrpBom bom = requireBom(id);
        if (!MrpConstants.BOM_ACTIVE.equals(bom.getStatus())) {
            throw BusinessException.ofKey("erp.mrp.bom-only-audited-disable");
        }
        bom.setStatus(MrpConstants.BOM_DISABLED);
        bomMapper.updateById(bom);
    }

    @Override
    public BomTreeVO tree(Long id) {
        MrpBom bom = requireBom(id);
        List<MrpBomItem> rootItems = listItems(id);
        // 物料信息缓存（懒加载补充子级 BOM 的物料）
        Map<Long, MaterialBriefVO> cache = new HashMap<>(lookup.materialMap(
                rootItems.stream().map(MrpBomItem::getMaterialId).toList()));
        cache.putAll(lookup.materialMap(List.of(bom.getMaterialId())));
        return expand(bom.getMaterialId(), 0, BigDecimal.ONE, BigDecimal.ZERO, bom.getVersion(),
                rootItems, new HashSet<>(), cache);
    }

    @Override
    public MrpBom findLatestActive(Long materialId) {
        return bomMapper.selectOne(Wrappers.lambdaQuery(MrpBom.class)
                .eq(MrpBom::getMaterialId, materialId)
                .eq(MrpBom::getStatus, MrpConstants.BOM_ACTIVE)
                .orderByDesc(MrpBom::getId)
                .last("LIMIT 1"));
    }

    /**
     * 递归展开树节点。rootItems 为当前层 BOM 的子件（根层外部传入，下层取子件最新有效 BOM）。
     * 防环：ancestorMaterials 保存根到当前的物料链，子件命中即抛循环引用异常。
     */
    private BomTreeVO expand(Long materialId, int level, BigDecimal qty, BigDecimal lossRate,
                             String bomVersion, List<MrpBomItem> items,
                             Set<Long> ancestorMaterials, Map<Long, MaterialBriefVO> cache) {
        if (ancestorMaterials.contains(materialId)) {
            throw BusinessException.ofKey("erp.mrp.bom-circular-level", materialId, level);
        }
        if (level > MrpConstants.MAX_BOM_LEVEL) {
            throw BusinessException.ofKey("erp.mrp.bom-explode-over-limit2", MrpConstants.MAX_BOM_LEVEL);
        }
        MaterialBriefVO material = cache.computeIfAbsent(materialId,
                mid -> lookup.materialMap(List.of(mid)).get(mid));
        BomTreeVO node = new BomTreeVO();
        node.setMaterialId(materialId);
        node.setCode(material == null ? null : material.getCode());
        node.setName(material == null ? ("物料#" + materialId) : material.getName());
        node.setUnit(material == null ? null : material.getUnit());
        node.setLevel(level);
        node.setQty(qty);
        node.setLossRate(lossRate);
        node.setBomVersion(bomVersion);

        Set<Long> nextAncestors = new HashSet<>(ancestorMaterials);
        nextAncestors.add(materialId);
        for (MrpBomItem item : items) {
            // 下层展开依据：子件自己的最新有效 BOM（无则为采购件叶子）
            MrpBom childBom = findLatestActive(item.getMaterialId());
            List<MrpBomItem> childItems = childBom == null ? List.of() : listItems(childBom.getId());
            node.getChildren().add(expand(item.getMaterialId(), level + 1, item.getQty(),
                    item.getLossRate(), childBom == null ? null : childBom.getVersion(),
                    childItems, nextAncestors, cache));
        }
        return node;
    }

    // ==================== 私有工具 ====================

    private MrpBom requireBom(Long id) {
        MrpBom bom = bomMapper.selectById(id);
        if (bom == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.bom-not-exists", id);
        }
        return bom;
    }

    /** 删除 BOM 全部子件行及其替代组（明细重建/删除时级联，V621） */
    private void deleteItemsWithSubstitutes(Long bomId) {
        List<MrpBomItem> items = listItems(bomId);
        if (!items.isEmpty()) {
            substituteMapper.delete(Wrappers.lambdaQuery(MrpBomSubstitute.class)
                    .in(MrpBomSubstitute::getBomItemId,
                            items.stream().map(MrpBomItem::getId).toList()));
        }
        bomItemMapper.delete(Wrappers.lambdaQuery(MrpBomItem.class).eq(MrpBomItem::getBomId, bomId));
    }

    private List<MrpBomItem> listItems(Long bomId) {
        return bomItemMapper.selectList(Wrappers.lambdaQuery(MrpBomItem.class)
                .eq(MrpBomItem::getBomId, bomId).orderByAsc(MrpBomItem::getId));
    }

    private Map<Long, Long> countItems(List<Long> bomIds) {
        if (bomIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (MrpBomItem item : bomItemMapper.selectList(Wrappers.lambdaQuery(MrpBomItem.class)
                .in(MrpBomItem::getBomId, bomIds))) {
            counts.merge(item.getBomId(), 1L, Long::sum);
        }
        return counts;
    }

    private void insertItems(Long bomId, BomForm form) {
        for (BomItemForm itemForm : form.getItems()) {
            MrpBomItem item = new MrpBomItem();
            item.setBomId(bomId);
            item.setMaterialId(itemForm.getMaterialId());
            item.setQty(itemForm.getQty());
            item.setLossRate(itemForm.getLossRate() == null ? BigDecimal.ZERO : itemForm.getLossRate());
            item.setRemark(itemForm.getRemark());
            bomItemMapper.insert(item);
        }
    }

    /** 表单校验：物料存在、子件不等于成品、版本唯一 */
    private void validateForm(Long excludeId, BomForm form) {
        Set<Long> ids = new HashSet<>();
        ids.add(form.getMaterialId());
        form.getItems().forEach(i -> ids.add(i.getMaterialId()));
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(ids);
        if (!materials.containsKey(form.getMaterialId())) {
            throw BusinessException.ofKey("erp.mrp.finished-material-not-exists", form.getMaterialId());
        }
        for (BomItemForm itemForm : form.getItems()) {
            if (itemForm.getMaterialId().equals(form.getMaterialId())) {
                throw BusinessException.ofKey("erp.mrp.component-not-same-as-finished");
            }
            if (!materials.containsKey(itemForm.getMaterialId())) {
                throw BusinessException.ofKey("erp.mrp.component-not-exists", itemForm.getMaterialId());
            }
        }
        if (versionExists(form.getMaterialId(), form.getVersion(), excludeId)) {
            throw BusinessException.ofKey("erp.mrp.bom-version-exists", form.getVersion());
        }
    }

    private boolean versionExists(Long materialId, String version, Long excludeId) {
        Long count = bomMapper.selectCount(Wrappers.lambdaQuery(MrpBom.class)
                .eq(MrpBom::getMaterialId, materialId)
                .eq(MrpBom::getVersion, version)
                .ne(excludeId != null, MrpBom::getId, excludeId));
        return count != null && count > 0;
    }

    private BomVO toVO(MrpBom bom, Long itemCount, Map<Long, MaterialBriefVO> materials) {
        BomVO vo = new BomVO();
        vo.setId(bom.getId());
        vo.setMaterialId(bom.getMaterialId());
        MaterialBriefVO m = materials.get(bom.getMaterialId());
        if (m != null) {
            vo.setMaterialCode(m.getCode());
            vo.setMaterialName(m.getName());
            vo.setUnit(m.getUnit());
        }
        vo.setVersion(bom.getVersion());
        vo.setStatus(bom.getStatus());
        vo.setRemark(bom.getRemark());
        vo.setCreateTime(bom.getCreateTime());
        vo.setItemCount(itemCount);
        return vo;
    }
}

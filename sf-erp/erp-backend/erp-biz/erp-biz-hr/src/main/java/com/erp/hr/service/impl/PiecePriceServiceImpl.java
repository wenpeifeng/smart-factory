package com.erp.hr.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.hr.constant.HrConstants;
import com.erp.hr.dto.form.PiecePriceForm;
import com.erp.hr.dto.vo.MaterialBriefVO;
import com.erp.hr.dto.vo.PiecePriceVO;
import com.erp.hr.entity.HrPiecePrice;
import com.erp.hr.mapper.HrIntegrationMapper;
import com.erp.hr.mapper.HrPiecePriceMapper;
import com.erp.hr.service.PiecePriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计件价目服务实现 —— 物料存在性校验走集成网关只读 base_material；
 * 同物料同工序唯一（uk），重复提交给出业务错误提示。
 */
@Service
@RequiredArgsConstructor
public class PiecePriceServiceImpl implements PiecePriceService {

    private final HrPiecePriceMapper priceMapper;
    private final HrIntegrationMapper integrationMapper;

    @Override
    public PageResult<PiecePriceVO> page(long pageNum, long pageSize, String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        // keyword 先解析为物料ID集合，再合并工序名模糊（OR 语义在 wrapper.and 中实现）
        List<Long> materialIds = hasKeyword
                ? integrationMapper.selectMaterials(keyword).stream().map(MaterialBriefVO::getId).toList()
                : List.of();
        var wrapper = Wrappers.lambdaQuery(HrPiecePrice.class)
                .eq(status != null && !status.isBlank(), HrPiecePrice::getStatus, status)
                .and(hasKeyword, w -> {
                    w.like(HrPiecePrice::getProcessName, keyword);
                    if (!materialIds.isEmpty()) {
                        w.or().in(HrPiecePrice::getMaterialId, materialIds);
                    }
                })
                .orderByDesc(HrPiecePrice::getId);
        PageResult<HrPiecePrice> page = PageUtils.of(priceMapper, wrapper, pageNum, pageSize);
        Map<Long, MaterialBriefVO> materials = materialMap(page.getRecords());
        return page.map(p -> toVO(p, materials));
    }

    @Override
    public PiecePriceVO detail(Long id) {
        return toVO(requirePrice(id), materialMap(List.of()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PiecePriceForm form) {
        requireMaterial(form.getMaterialId());
        requireUnique(form.getMaterialId(), form.getProcessName(), null);
        HrPiecePrice p = new HrPiecePrice();
        applyForm(p, form);
        p.setStatus(defaultStatus(form.getStatus()));
        try {
            priceMapper.insert(p);
        } catch (DuplicateKeyException e) {
            // 历史逻辑删除行仍占用 uk(material_id, process_name)，给出业务提示而非 500
            throw BusinessException.ofKey("erp.hr.piece-rate-exists-historical", form.getProcessName());
        }
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PiecePriceForm form) {
        requirePrice(id);
        requireMaterial(form.getMaterialId());
        requireUnique(form.getMaterialId(), form.getProcessName(), id);
        HrPiecePrice p = new HrPiecePrice();
        p.setId(id);
        applyForm(p, form);
        p.setStatus(defaultStatus(form.getStatus()));
        try {
            priceMapper.updateById(p);
        } catch (DuplicateKeyException e) {
            throw BusinessException.ofKey("erp.hr.piece-rate-exists-historical", form.getProcessName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requirePrice(id);
        priceMapper.deleteById(id);
    }

    // ==================== 私有工具 ====================

    private HrPiecePrice requirePrice(Long id) {
        HrPiecePrice p = priceMapper.selectById(id);
        if (p == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.hr.piece-rate-not-exists", id);
        }
        return p;
    }

    private void requireMaterial(Long materialId) {
        if (integrationMapper.selectMaterial(materialId) == null) {
            throw BusinessException.ofKey("erp.hr.material-not-exists", materialId);
        }
    }

    /** 同物料同工序唯一校验（排除自身） */
    private void requireUnique(Long materialId, String processName, Long excludeId) {
        Long dup = priceMapper.selectCount(Wrappers.lambdaQuery(HrPiecePrice.class)
                .eq(HrPiecePrice::getMaterialId, materialId)
                .eq(HrPiecePrice::getProcessName, processName)
                .ne(excludeId != null, HrPiecePrice::getId, excludeId));
        if (dup != null && dup > 0) {
            throw BusinessException.ofKey("erp.hr.piece-rate-exists", processName);
        }
    }

    private String defaultStatus(String status) {
        return status == null || status.isBlank() ? HrConstants.PRICE_ENABLED : status;
    }

    private void applyForm(HrPiecePrice p, PiecePriceForm form) {
        p.setMaterialId(form.getMaterialId());
        p.setProcessName(form.getProcessName().trim());
        p.setUnitPrice(form.getUnitPrice());
        p.setRemark(form.getRemark());
    }

    private Map<Long, MaterialBriefVO> materialMap(List<HrPiecePrice> prices) {
        Map<Long, MaterialBriefVO> map = new HashMap<>();
        for (HrPiecePrice p : prices) {
            if (p.getMaterialId() != null && !map.containsKey(p.getMaterialId())) {
                map.put(p.getMaterialId(), integrationMapper.selectMaterial(p.getMaterialId()));
            }
        }
        return map;
    }

    private PiecePriceVO toVO(HrPiecePrice p, Map<Long, MaterialBriefVO> materials) {
        PiecePriceVO vo = new PiecePriceVO();
        vo.setId(p.getId());
        vo.setMaterialId(p.getMaterialId());
        MaterialBriefVO m = materials.get(p.getMaterialId());
        if (m == null && p.getMaterialId() != null) {
            m = integrationMapper.selectMaterial(p.getMaterialId());
        }
        if (m != null) {
            vo.setMaterialCode(m.getCode());
            vo.setMaterialName(m.getName());
        }
        vo.setProcessName(p.getProcessName());
        vo.setUnitPrice(p.getUnitPrice());
        vo.setStatus(p.getStatus());
        vo.setRemark(p.getRemark());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }
}

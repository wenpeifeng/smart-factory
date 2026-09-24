package com.erp.mrp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.mrp.constant.MrpConstants;
import com.erp.mrp.dto.form.MpsForm;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.MpsVO;
import com.erp.mrp.entity.MrpMps;
import com.erp.mrp.mapper.MrpIntegrationMapper;
import com.erp.mrp.mapper.MrpMpsMapper;
import com.erp.mrp.service.MpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * MPS 服务实现。
 */
@Service
@RequiredArgsConstructor
public class MpsServiceImpl implements MpsService {

    private final MrpMpsMapper mpsMapper;
    private final MrpIntegrationMapper integrationMapper;
    private final MrpLookupHelper lookup;

    @Override
    public PageResult<MpsVO> page(long pageNum, long pageSize, String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        List<Long> materialIds = hasKeyword ? integrationMapper.selectMaterialIds(keyword) : List.of();
        if (hasKeyword && materialIds.isEmpty()) {
            return PageResult.of(List.of(), 0, pageNum, pageSize);
        }
        var wrapper = Wrappers.lambdaQuery(MrpMps.class)
                .eq(status != null && !status.isBlank(), MrpMps::getStatus, status)
                .in(hasKeyword, MrpMps::getMaterialId, materialIds)
                .orderByDesc(MrpMps::getId);
        PageResult<MrpMps> page = PageUtils.of(mpsMapper, wrapper, pageNum, pageSize);
        Map<Long, MaterialBriefVO> materials = lookup.materialMap(
                page.getRecords().stream().map(MrpMps::getMaterialId).toList());
        return page.map(mps -> {
            MpsVO vo = new MpsVO();
            vo.setId(mps.getId());
            vo.setMaterialId(mps.getMaterialId());
            MaterialBriefVO m = materials.get(mps.getMaterialId());
            if (m != null) {
                vo.setMaterialCode(m.getCode());
                vo.setMaterialName(m.getName());
                vo.setUnit(m.getUnit());
            }
            vo.setQty(mps.getQty());
            vo.setStartDate(mps.getStartDate());
            vo.setStatus(mps.getStatus());
            vo.setRemark(mps.getRemark());
            vo.setCreateTime(mps.getCreateTime());
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MpsForm form) {
        validateMaterial(form.getMaterialId());
        MrpMps mps = new MrpMps();
        applyForm(mps, form);
        mps.setStatus(MrpConstants.MPS_PENDING);
        mpsMapper.insert(mps);
        return mps.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MpsForm form) {
        requireMps(id);
        validateMaterial(form.getMaterialId());
        MrpMps mps = new MrpMps();
        mps.setId(id);
        applyForm(mps, form);
        mpsMapper.updateById(mps);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireMps(id);
        mpsMapper.deleteById(id);
    }

    private void applyForm(MrpMps mps, MpsForm form) {
        mps.setMaterialId(form.getMaterialId());
        mps.setQty(form.getQty());
        mps.setStartDate(form.getStartDate());
        mps.setRemark(form.getRemark());
    }

    private MrpMps requireMps(Long id) {
        MrpMps mps = mpsMapper.selectById(id);
        if (mps == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.mrp.mps-line-not-exists", id);
        }
        return mps;
    }

    private void validateMaterial(Long materialId) {
        if (lookup.materialMap(List.of(materialId)).isEmpty()) {
            throw BusinessException.ofKey("erp.mrp.finished-material-not-exists", materialId);
        }
    }
}

package com.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.plan.entity.PlanWoMaterial;
import com.mes.plan.mapper.PlanWoMaterialMapper;
import com.mes.plan.service.WoMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 工单用料需求服务实现。
 */
@Service
@RequiredArgsConstructor
public class WoMaterialServiceImpl implements WoMaterialService {

    private final PlanWoMaterialMapper woMaterialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addIssued(Long workorderId, Long materialId, BigDecimal qty) {
        PlanWoMaterial wom = woMaterialMapper.selectOne(Wrappers.lambdaQuery(PlanWoMaterial.class)
                .eq(PlanWoMaterial::getWorkorderId, workorderId)
                .eq(PlanWoMaterial::getMaterialId, materialId)
                .last("LIMIT 1"));
        if (wom == null) {
            // 工单 BOM 外领料允许（辅料等），仅跳过回写，不阻断领料审核
            return;
        }
        wom.setIssuedQty((wom.getIssuedQty() == null ? BigDecimal.ZERO : wom.getIssuedQty()).add(qty));
        if (wom.getIssuedQty().compareTo(wom.getReqQty()) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "物料 %d 累计领料 %s 超过工单需求 %s", materialId, wom.getIssuedQty(), wom.getReqQty());
        }
        woMaterialMapper.updateById(wom);
    }
}

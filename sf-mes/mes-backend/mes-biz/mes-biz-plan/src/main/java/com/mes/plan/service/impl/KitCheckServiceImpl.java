package com.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.plan.dto.vo.KitCheckDetailVO;
import com.mes.plan.dto.vo.KitCheckVO;
import com.mes.plan.entity.PlanKitCheck;
import com.mes.plan.entity.PlanKitCheckItem;
import com.mes.plan.entity.PlanWoMaterial;
import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanKitCheckItemMapper;
import com.mes.plan.mapper.PlanKitCheckMapper;
import com.mes.plan.mapper.PlanWoMaterialMapper;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.plan.service.KitCheckService;
import com.mes.plan.service.MesNoGenerator;
import com.mes.plan.service.StockAvailabilityService;
import com.mes.plan.service.impl.WorkorderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 齐套分析服务实现。
 * <p>逐料比较：需求量（已扣已领）vs 可用量（线边仓）；全部满足=FULL，
 * 全部短缺=SHORT，部分短缺=PART。
 */
@Service
@RequiredArgsConstructor
public class KitCheckServiceImpl implements KitCheckService {

    private final PlanKitCheckMapper kitCheckMapper;
    private final PlanKitCheckItemMapper kitCheckItemMapper;
    private final PlanWorkorderMapper workorderMapper;
    private final PlanWoMaterialMapper woMaterialMapper;
    private final BaseMaterialMapper materialMapper;
    private final StockAvailabilityService stockAvailabilityService;
    private final MesNoGenerator noGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long check(Long workorderId) {
        PlanWorkorder wo = workorderMapper.selectById(workorderId);
        if (wo == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单不存在: %d".formatted(workorderId));
        }
        if (WorkorderServiceImpl.ST_DRAFT.equals(wo.getStatus())) {
            throw new BusinessException("草稿工单请先下达再做齐套分析");
        }
        List<PlanWoMaterial> requirements = woMaterialMapper.selectList(
                Wrappers.lambdaQuery(PlanWoMaterial.class)
                        .eq(PlanWoMaterial::getWorkorderId, workorderId));
        if (requirements.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单 %s 无用料需求（下达时未展开 BOM）", wo.getWoNo());
        }

        PlanKitCheck check = new PlanKitCheck();
        check.setCheckNo(noGenerator.next(MesNoGenerator.PREFIX_KIT_CHECK));
        check.setWorkorderId(workorderId);
        check.setWoNo(wo.getWoNo());
        // result 列 NOT NULL 无默认值：先落占位 PART，逐料比对后回写最终结论
        check.setResult("PART");
        check.setShortCount(0);
        kitCheckMapper.insert(check);

        int shortCount = 0;
        for (PlanWoMaterial req : requirements) {
            // 未领需求 = 需求 - 已领
            BigDecimal remaining = req.getReqQty().subtract(
                    req.getIssuedQty() == null ? BigDecimal.ZERO : req.getIssuedQty());
            BigDecimal available = stockAvailabilityService.getAvailableQty(req.getMaterialId());
            BigDecimal shortQty = remaining.subtract(available).max(BigDecimal.ZERO);
            boolean isShort = shortQty.compareTo(BigDecimal.ZERO) > 0;
            if (isShort) {
                shortCount++;
            }
            PlanKitCheckItem item = new PlanKitCheckItem();
            item.setCheckId(check.getId());
            item.setMaterialId(req.getMaterialId());
            item.setReqQty(remaining);
            item.setAvailableQty(available);
            item.setShortQty(shortQty);
            item.setResult(isShort ? "SHORT" : "OK");
            kitCheckItemMapper.insert(item);
        }

        check.setShortCount(shortCount);
        check.setResult(shortCount == 0 ? "FULL"
                : shortCount == requirements.size() ? "SHORT" : "PART");
        kitCheckMapper.updateById(check);
        return check.getId();
    }

    @Override
    public PageResult<KitCheckVO> page(long pageNum, long pageSize, String keyword, String result) {
        var wrapper = Wrappers.lambdaQuery(PlanKitCheck.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(PlanKitCheck::getWoNo, keyword)
                        .or().like(PlanKitCheck::getCheckNo, keyword))
                .eq(StringUtils.hasText(result), PlanKitCheck::getResult, result)
                .orderByDesc(PlanKitCheck::getId);
        return PageUtils.of(kitCheckMapper, wrapper, pageNum, pageSize, KitCheckVO::from);
    }

    @Override
    public KitCheckDetailVO latestByWorkorder(Long workorderId) {
        PlanKitCheck check = kitCheckMapper.selectOne(Wrappers.lambdaQuery(PlanKitCheck.class)
                .eq(PlanKitCheck::getWorkorderId, workorderId)
                .orderByDesc(PlanKitCheck::getId)
                .last("LIMIT 1"));
        if (check == null) {
            return null;
        }
        KitCheckDetailVO vo = new KitCheckDetailVO();
        KitCheckVO base = KitCheckVO.from(check);
        org.springframework.beans.BeanUtils.copyProperties(base, vo);
        List<KitCheckDetailVO.ItemVO> items = kitCheckItemMapper.selectList(
                        Wrappers.lambdaQuery(PlanKitCheckItem.class)
                                .eq(PlanKitCheckItem::getCheckId, check.getId())
                                .orderByAsc(PlanKitCheckItem::getId))
                .stream().map(item -> {
                    KitCheckDetailVO.ItemVO iv = new KitCheckDetailVO.ItemVO();
                    iv.setId(item.getId());
                    iv.setMaterialId(item.getMaterialId());
                    iv.setReqQty(item.getReqQty());
                    iv.setAvailableQty(item.getAvailableQty());
                    iv.setShortQty(item.getShortQty());
                    iv.setResult(item.getResult());
                    return iv;
                }).toList();
        if (!items.isEmpty()) {
            Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                            items.stream().map(KitCheckDetailVO.ItemVO::getMaterialId).toList())
                    .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
            items.forEach(iv -> {
                BaseMaterial mat = mats.get(iv.getMaterialId());
                if (mat != null) {
                    iv.setMaterialCode(mat.getCode());
                    iv.setMaterialName(mat.getName());
                }
            });
        }
        vo.setItems(items);
        return vo;
    }
}

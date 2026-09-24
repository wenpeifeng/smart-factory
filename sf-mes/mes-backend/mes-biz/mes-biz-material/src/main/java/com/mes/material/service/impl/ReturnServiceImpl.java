package com.mes.material.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.integration.erp.dto.ErpReturnDTO;
import com.mes.integration.service.ErpPushService;
import com.mes.material.dto.form.ReturnForm;
import com.mes.material.dto.vo.ReturnVO;
import com.mes.material.entity.MatReturn;
import com.mes.material.entity.MatReturnItem;
import com.mes.material.mapper.MatReturnItemMapper;
import com.mes.material.mapper.MatReturnMapper;
import com.mes.material.service.ReturnService;
import com.mes.material.service.ShopStockService;
import com.mes.plan.entity.PlanWorkorder;
import com.mes.plan.mapper.PlanWorkorderMapper;
import com.mes.plan.service.MesNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 退料单服务实现（余料/不良退回仓库，线边仓扣减后回传 ERP）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private static final Long DEFAULT_WC = 0L;

    private final MatReturnMapper returnMapper;
    private final MatReturnItemMapper returnItemMapper;
    private final PlanWorkorderMapper workorderMapper;
    private final BaseMaterialMapper materialMapper;
    private final ShopStockService shopStockService;
    private final MesNoGenerator noGenerator;
    private final ErpPushService erpPushService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReturnForm form) {
        MatReturn order = new MatReturn();
        order.setReturnNo(noGenerator.next(MesNoGenerator.PREFIX_RETURN));
        if (form.getWorkorderId() != null) {
            PlanWorkorder wo = workorderMapper.selectById(form.getWorkorderId());
            if (wo == null) {
                throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "工单不存在: %d".formatted(form.getWorkorderId()));
            }
            order.setWorkorderId(wo.getId());
            order.setWoNo(wo.getWoNo());
        }
        order.setReturnType(StringUtils.hasText(form.getReturnType()) ? form.getReturnType() : "LEFTOVER");
        order.setWarehouseId(form.getWarehouseId());
        order.setStatus("DRAFT");
        order.setRemark(form.getRemark());
        order.setErpPushed(0);
        returnMapper.insert(order);
        for (ReturnForm.Item item : form.getItems()) {
            MatReturnItem row = new MatReturnItem();
            row.setReturnId(order.getId());
            row.setMaterialId(item.getMaterialId());
            row.setBatchNo(item.getBatchNo() == null ? "" : item.getBatchNo());
            row.setQty(item.getQty());
            row.setRemark(item.getRemark());
            returnItemMapper.insert(row);
        }
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MatReturn order = requireReturn(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException("仅草稿退料单可删除");
        }
        returnItemMapper.delete(Wrappers.lambdaQuery(MatReturnItem.class)
                .eq(MatReturnItem::getReturnId, id));
        returnMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        MatReturn order = requireReturn(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException("退料单已审核，不可重复操作");
        }
        List<MatReturnItem> items = returnItemMapper.selectList(
                Wrappers.lambdaQuery(MatReturnItem.class).eq(MatReturnItem::getReturnId, id));
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "退料单 %s 无行项目", order.getReturnNo());
        }
        for (MatReturnItem item : items) {
            shopStockService.stockOut(item.getMaterialId(), item.getBatchNo(), DEFAULT_WC, item.getQty());
        }
        order.setStatus("AUDITED");
        order.setAuditTime(LocalDateTime.now());
        order.setAuditor("系统");
        returnMapper.updateById(order);

        try {
            Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                            items.stream().map(MatReturnItem::getMaterialId).toList())
                    .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
            ErpReturnDTO dto = new ErpReturnDTO();
            dto.setReturnNo(order.getReturnNo());
            dto.setWoNo(order.getWoNo());
            dto.setWarehouseCode(String.valueOf(order.getWarehouseId()));
            dto.setItems(items.stream().map(i -> {
                ErpReturnDTO.Item di = new ErpReturnDTO.Item();
                di.setMaterialCode(mats.containsKey(i.getMaterialId())
                        ? mats.get(i.getMaterialId()).getCode() : String.valueOf(i.getMaterialId()));
                di.setBatchNo(i.getBatchNo());
                di.setQty(i.getQty());
                return di;
            }).toList());
            erpPushService.pushReturn(order.getId(), dto);
            order.setErpPushed(1);
            returnMapper.updateById(order);
        } catch (Exception ex) {
            log.warn("[RETURN] 退料回传 ERP 失败（不影响审核）: {}", ex.getMessage());
        }
    }

    @Override
    public PageResult<ReturnVO> page(long pageNum, long pageSize, String keyword, String status) {
        var wrapper = Wrappers.lambdaQuery(MatReturn.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(MatReturn::getReturnNo, keyword)
                        .or().like(MatReturn::getWoNo, keyword))
                .eq(StringUtils.hasText(status), MatReturn::getStatus, status)
                .orderByDesc(MatReturn::getId);
        return PageUtils.of(returnMapper, wrapper, pageNum, pageSize, ReturnVO::from);
    }

    @Override
    public ReturnVO detail(Long id) {
        ReturnVO vo = ReturnVO.from(requireReturn(id));
        List<ReturnVO.ItemVO> items = returnItemMapper.selectList(
                        Wrappers.lambdaQuery(MatReturnItem.class).eq(MatReturnItem::getReturnId, id))
                .stream().map(ReturnVO.ItemVO::from).toList();
        if (!items.isEmpty()) {
            Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                            items.stream().map(ReturnVO.ItemVO::getMaterialId).toList())
                    .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
            items.forEach(i -> {
                BaseMaterial mat = mats.get(i.getMaterialId());
                if (mat != null) {
                    i.setMaterialCode(mat.getCode());
                    i.setMaterialName(mat.getName());
                }
            });
        }
        vo.setItems(items);
        return vo;
    }

    private MatReturn requireReturn(Long id) {
        MatReturn order = returnMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "退料单不存在: %d".formatted(id));
        }
        return order;
    }
}

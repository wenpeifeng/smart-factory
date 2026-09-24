package com.mes.material.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.material.dto.form.BatchSnForm;
import com.mes.material.dto.vo.BatchSnVO;
import com.mes.material.entity.MatBatchSn;
import com.mes.material.mapper.MatBatchSnMapper;
import com.mes.material.service.BatchSnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批次/SN 档案服务实现（登记 + 查询；正反向追溯消费在 P3 trace 模块）。
 */
@Service
@RequiredArgsConstructor
public class BatchSnServiceImpl implements BatchSnService {

    private final MatBatchSnMapper batchSnMapper;
    private final BaseMaterialMapper materialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(BatchSnForm form) {
        boolean isSn = "SN".equals(form.getObjType());
        if (!isSn && !"BATCH".equals(form.getObjType())) {
            throw new BusinessException("对象类型必须为 BATCH 或 SN");
        }
        MatBatchSn record = new MatBatchSn();
        record.setObjType(form.getObjType());
        // objNo 由调用方给定（扫码/上游单据）或系统生成时间戳号，保证唯一即可
        record.setObjNo(StringUtils.hasText(form.getObjNo())
                ? form.getObjNo()
                : (isSn ? "SN" : "PC") + System.currentTimeMillis());
        record.setMaterialId(form.getMaterialId());
        record.setSourceType(form.getSourceType());
        record.setSourceNo(form.getSourceNo());
        record.setWorkorderId(form.getWorkorderId());
        record.setQty(isSn ? BigDecimal.ONE : form.getQty());
        record.setSupplierId(form.getSupplierId());
        record.setStatus("IN_STOCK");
        record.setParentSn(form.getParentSn());
        batchSnMapper.insert(record);
        return record.getId();
    }

    @Override
    public PageResult<BatchSnVO> page(long pageNum, long pageSize, String keyword, String objType, Long materialId) {
        var wrapper = Wrappers.lambdaQuery(MatBatchSn.class)
                .like(StringUtils.hasText(keyword), MatBatchSn::getObjNo, keyword)
                .eq(StringUtils.hasText(objType), MatBatchSn::getObjType, objType)
                .eq(materialId != null, MatBatchSn::getMaterialId, materialId)
                .orderByDesc(MatBatchSn::getId);
        PageResult<BatchSnVO> page = PageUtils.of(batchSnMapper, wrapper, pageNum, pageSize, BatchSnVO::from);
        if (!page.getRecords().isEmpty()) {
            Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                            page.getRecords().stream().map(BatchSnVO::getMaterialId).toList())
                    .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
            page.getRecords().forEach(vo -> {
                BaseMaterial mat = mats.get(vo.getMaterialId());
                if (mat != null) {
                    vo.setMaterialCode(mat.getCode());
                    vo.setMaterialName(mat.getName());
                }
            });
        }
        return page;
    }
}

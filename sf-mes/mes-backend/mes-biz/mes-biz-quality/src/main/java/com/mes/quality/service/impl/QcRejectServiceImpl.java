package com.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.quality.dto.form.QcRejectForm;
import com.mes.quality.dto.vo.QcRejectVO;
import com.mes.quality.entity.QcInspect;
import com.mes.quality.entity.QcReject;
import com.mes.quality.mapper.QcInspectMapper;
import com.mes.quality.mapper.QcRejectMapper;
import com.mes.quality.service.QcNoGenerator;
import com.mes.quality.service.QcRejectService;
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
 * 不合格品处置单服务实现（处置累计数不得超过检验单不合格数）。
 */
@Service
@RequiredArgsConstructor
public class QcRejectServiceImpl implements QcRejectService {

    private final QcRejectMapper rejectMapper;
    private final QcInspectMapper inspectMapper;
    private final BaseMaterialMapper materialMapper;
    private final QcNoGenerator noGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QcRejectForm form) {
        QcInspect inspect = inspectMapper.selectById(form.getInspectId());
        if (inspect == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "检验单不存在: %d".formatted(form.getInspectId()));
        }
        if (inspect.getStatus() == null || inspect.getStatus() != 1) {
            throw new BusinessException("检验单未提交，不能发起不合格处置");
        }
        BigDecimal handled = rejectMapper.selectList(Wrappers.lambdaQuery(QcReject.class)
                        .eq(QcReject::getInspectId, inspect.getId()))
                .stream().map(QcReject::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (handled.add(form.getQty()).compareTo(
                inspect.getUnqualifiedQty() == null ? BigDecimal.ZERO : inspect.getUnqualifiedQty()) > 0) {
            throw new BusinessException("处置数量累计超过检验单不合格数（已处置 %s）", handled.toPlainString());
        }
        QcReject reject = new QcReject();
        reject.setRejectNo(noGenerator.next(QcNoGenerator.PREFIX_REJECT));
        reject.setInspectId(inspect.getId());
        reject.setInspectNo(inspect.getInspectNo());
        reject.setMaterialId(inspect.getMaterialId());
        reject.setQty(form.getQty());
        reject.setDecision(form.getDecision());
        reject.setDisposeRemark(form.getDisposeRemark());
        reject.setApprover(form.getApprover());
        rejectMapper.insert(reject);
        return reject.getId();
    }

    @Override
    public PageResult<QcRejectVO> page(long pageNum, long pageSize, String keyword) {
        var wrapper = Wrappers.lambdaQuery(QcReject.class)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(QcReject::getRejectNo, keyword)
                        .or().like(QcReject::getInspectNo, keyword))
                .orderByDesc(QcReject::getId);
        PageResult<QcRejectVO> page = PageUtils.of(rejectMapper, wrapper, pageNum, pageSize, QcRejectVO::from);
        if (!page.getRecords().isEmpty()) {
            Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                            page.getRecords().stream().map(QcRejectVO::getMaterialId).toList())
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

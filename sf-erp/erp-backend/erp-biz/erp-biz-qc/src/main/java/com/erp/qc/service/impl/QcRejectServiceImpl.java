package com.erp.qc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.qc.dto.form.QcRejectForm;
import com.erp.qc.dto.query.QcRejectQuery;
import com.erp.qc.dto.vo.QcRejectVO;
import com.erp.qc.dto.vo.RejectSourceVO;
import com.erp.qc.entity.QcInspect;
import com.erp.qc.entity.QcReject;
import com.erp.qc.mapper.QcInspectMapper;
import com.erp.qc.mapper.QcRejectMapper;
import com.erp.qc.service.QcNoGenerator;
import com.erp.qc.service.QcRejectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 不合格品处置 Service 实现。
 * <p>
 * 数量守卫：同一检验单累计处置数量 ≤ 该检验单不合格数量。
 * 新建传 excludeId=0 计算存量，修改传自身 ID 排除（否则自身也会占用余额导致无法调小）。
 */
@Service
@RequiredArgsConstructor
public class QcRejectServiceImpl implements QcRejectService {

    private final QcRejectMapper rejectMapper;
    private final QcInspectMapper inspectMapper;
    private final QcNoGenerator noGenerator;

    @Override
    public PageResult<QcRejectVO> page(QcRejectQuery query) {
        var page = rejectMapper.selectRejectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public QcRejectVO getDetail(Long id) {
        return requireRejectDetail(id);
    }

    @Override
    public List<RejectSourceVO> listSources() {
        return rejectMapper.selectRejectSources();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QcRejectForm form) {
        QcInspect inspect = requireRejectableInspect(form.getInspectId());
        validateQty(inspect, form.getQty(), 0L);
        QcReject reject = build(form, inspect);
        rejectMapper.insert(reject);
        return reject.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, QcRejectForm form) {
        QcReject exists = requireReject(id);
        if (!exists.getInspectId().equals(form.getInspectId())) {
            throw BusinessException.ofKey("erp.qc.disposal-source-immutable");
        }
        QcInspect inspect = requireRejectableInspect(form.getInspectId());
        validateQty(inspect, form.getQty(), id);
        QcReject reject = build(form, inspect);
        // 修改沿用原单号（build 里生成的新号仅新建使用）
        reject.setRejectNo(exists.getRejectNo());
        reject.setId(id);
        rejectMapper.updateById(reject);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireReject(id);
        rejectMapper.deleteById(id);
    }

    /** 加载处置单，不存在抛业务异常 */
    private QcReject requireReject(Long id) {
        QcReject reject = rejectMapper.selectById(id);
        if (reject == null) {
            throw BusinessException.ofKey("erp.qc.disposal-not-exists", id);
        }
        return reject;
    }

    /** 详情查询（XML JOIN 带出物料信息），不存在抛业务异常 */
    private QcRejectVO requireRejectDetail(Long id) {
        QcRejectVO vo = rejectMapper.selectRejectById(id);
        if (vo == null) {
            throw BusinessException.ofKey("erp.qc.disposal-not-exists", id);
        }
        return vo;
    }

    /** 加载可处置的检验单：已检验且不合格数>0 */
    private QcInspect requireRejectableInspect(Long inspectId) {
        QcInspect inspect = inspectMapper.selectById(inspectId);
        if (inspect == null) {
            throw BusinessException.ofKey("erp.qc.source-inspection-not-exists", inspectId);
        }
        if (inspect.getStatus() != QcInspect.STATUS_INSPECTED) {
            throw BusinessException.ofKey("erp.qc.inspection-not-submitted", inspect.getInspectNo());
        }
        if (inspect.getUnqualifiedQty() == null || inspect.getUnqualifiedQty().signum() <= 0) {
            throw BusinessException.ofKey("erp.qc.no-unqualified", inspect.getInspectNo());
        }
        return inspect;
    }

    /** 数量守卫：本次数量 + 已处置数量（排除自身） ≤ 不合格数 */
    private void validateQty(QcInspect inspect, BigDecimal qty, Long excludeId) {
        BigDecimal disposed = rejectMapper.sumDisposedQty(inspect.getId(), excludeId);
        BigDecimal remain = inspect.getUnqualifiedQty().subtract(disposed);
        if (qty.compareTo(remain) > 0) {
            throw BusinessException.ofKey("erp.qc.dispose-qty-over", qty.toPlainString(), inspect.getInspectNo(), remain.toPlainString());
        }
    }

    /** 表单 → 实体（单号仅新建时生成；修改沿用原单号） */
    private QcReject build(QcRejectForm form, QcInspect inspect) {
        QcReject reject = new QcReject();
        reject.setRejectNo(noGenerator.next(QcNoGenerator.PREFIX_REJECT));
        reject.setInspectId(inspect.getId());
        reject.setInspectNo(inspect.getInspectNo());
        reject.setMaterialId(inspect.getMaterialId());
        reject.setQty(form.getQty());
        reject.setDecision(form.getDecision());
        reject.setDisposeRemark(form.getDisposeRemark());
        reject.setApprover(form.getApprover());
        return reject;
    }
}

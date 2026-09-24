package com.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.dto.form.OtherOutForm;
import com.erp.inventory.dto.request.OtherDocQuery;
import com.erp.inventory.dto.vo.OtherDocDetailVO;
import com.erp.inventory.dto.vo.OtherDocVO;
import com.erp.inventory.entity.InvOtherOut;
import com.erp.inventory.entity.InvOtherOutLine;
import com.erp.inventory.mapper.InvOtherOutLineMapper;
import com.erp.inventory.mapper.InvOtherOutMapper;
import com.erp.inventory.service.InvOtherOutService;
import com.erp.inventory.service.StockService;
import com.erp.inventory.support.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 其他出库单服务实现。
 * <p>
 * 状态机：0 草稿 → 1 已审核（单向）。审核时逐行扣库存（OTH_OUT，负数变动），
 * 任一行库存不足则整个审核事务回滚（单据保持草稿态）。
 * <p>
 * 二期（V411）：行可带批次号 batchNo——
 * 批次物料指定批次 = BATCH 策略（该批次不足整体回滚）；未指定 = FIFO 自动分配（可拆多行流水）；
 * 非批次物料无论是否传批次都走汇总行原逻辑。
 */
@Service
@RequiredArgsConstructor
public class InvOtherOutServiceImpl implements InvOtherOutService {

    private final InvOtherOutMapper docMapper;
    private final InvOtherOutLineMapper lineMapper;
    private final StockService stockService;
    private final BizNoGenerator bizNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OtherOutForm form) {
        InvOtherOut doc = new InvOtherOut();
        doc.setNo(bizNoGenerator.next("OO"));
        doc.setWarehouseId(form.getWarehouseId());
        doc.setReason(form.getReason());
        doc.setStrategy(resolveFormStrategy(form.getStrategy()).getCode());
        doc.setRemark(form.getRemark());
        doc.setStatus(InvOtherOut.STATUS_DRAFT);
        docMapper.insert(doc);

        for (OtherOutForm.Line line : form.getLines()) {
            InvOtherOutLine entity = new InvOtherOutLine();
            entity.setOtherOutId(doc.getId());
            entity.setMaterialId(line.getMaterialId());
            entity.setBatchNo(line.getBatchNo() == null ? "" : line.getBatchNo().trim());
            entity.setQty(line.getQty());
            entity.setRemark(line.getRemark());
            lineMapper.insert(entity);
        }
        return doc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InvOtherOut doc = requireDoc(id);
        if (doc.getStatus() != InvOtherOut.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.biz.doc-audited", doc.getNo());
        }
        List<InvOtherOutLine> lines = lineMapper.selectList(
                Wrappers.lambdaQuery(InvOtherOutLine.class).eq(InvOtherOutLine::getOtherOutId, id));
        if (lines.isEmpty()) {
            throw BusinessException.ofKey("erp.inv.doc-no-lines", doc.getNo());
        }
        // 逐行过账：扣库存 + 落 OTH_OUT 流水；行内自带余额校验，不足即抛异常回滚。
        // 批次物料：指定批次走 BATCH、未指定走表单策略（默认 FIFO，可 FEFO/AVG，V422）；
        // 非批次物料走汇总行（stockOut 内部按物料标志分流）。
        OutStrategy formStrategy = resolveFormStrategy(doc.getStrategy());
        for (InvOtherOutLine line : lines) {
            boolean hasBatch = line.getBatchNo() != null && !line.getBatchNo().isBlank();
            stockService.stockOut(doc.getWarehouseId(), line.getMaterialId(), line.getQty(),
                    hasBatch ? OutStrategy.BATCH : formStrategy, line.getBatchNo(),
                    InvBizType.OTH_OUT, doc.getId(), doc.getNo(), doc.getReason());
        }
        doc.setStatus(InvOtherOut.STATUS_AUDITED);
        doc.setAuditTime(LocalDateTime.now());
        docMapper.updateById(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InvOtherOut doc = requireDoc(id);
        if (doc.getStatus() != InvOtherOut.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.inv.doc-posted-no-delete", doc.getNo());
        }
        docMapper.deleteById(id);
        lineMapper.delete(Wrappers.lambdaQuery(InvOtherOutLine.class)
                .eq(InvOtherOutLine::getOtherOutId, id));
    }

    @Override
    public PageResult<OtherDocVO> page(OtherDocQuery query) {
        IPage<OtherDocVO> page = docMapper.selectDocPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public OtherDocDetailVO detail(Long id) {
        InvOtherOut doc = requireDoc(id);
        OtherDocDetailVO vo = new OtherDocDetailVO();
        vo.setId(doc.getId());
        vo.setNo(doc.getNo());
        vo.setWarehouseId(doc.getWarehouseId());
        vo.setReason(doc.getReason());
        vo.setStatus(doc.getStatus());
        vo.setAuditTime(doc.getAuditTime());
        vo.setRemark(doc.getRemark());
        vo.setCreateTime(doc.getCreateTime());
        vo.setLines(lineMapper.selectLinesByDocId(id).stream().map(l -> {
            OtherDocDetailVO.LineVO line = new OtherDocDetailVO.LineVO();
            line.setId(l.getId());
            line.setMaterialId(l.getMaterialId());
            line.setMaterialCode(l.getMaterialCode());
            line.setMaterialName(l.getMaterialName());
            line.setBatchNo(l.getBatchNo());
            line.setQty(l.getQty());
            line.setRemark(l.getRemark());
            return line;
        }).toList());
        return vo;
    }

    private InvOtherOut requireDoc(Long id) {
        InvOtherOut doc = docMapper.selectById(id);
        if (doc == null) {
            throw BusinessException.ofKey("erp.inv.oth-out-not-exists", id);
        }
        return doc;
    }

    /**
     * 出库策略解析（V422）：空/非法值一律回退 FIFO（与一期行为兼容）。
     */
    private OutStrategy resolveFormStrategy(String code) {
        if (code == null || code.isBlank()) {
            return OutStrategy.FIFO;
        }
        try {
            return OutStrategy.of(code.trim());
        } catch (IllegalArgumentException e) {
            return OutStrategy.FIFO;
        }
    }
}

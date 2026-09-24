package com.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.dto.form.OtherInForm;
import com.erp.inventory.dto.request.OtherDocQuery;
import com.erp.inventory.dto.vo.OtherDocDetailVO;
import com.erp.inventory.dto.vo.OtherDocVO;
import com.erp.inventory.entity.InvOtherIn;
import com.erp.inventory.entity.InvOtherInLine;
import com.erp.inventory.mapper.InvOtherInLineMapper;
import com.erp.inventory.mapper.InvOtherInMapper;
import com.erp.inventory.service.InvOtherInService;
import com.erp.inventory.service.StockService;
import com.erp.inventory.support.BizNoGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 其他入库单服务实现。
 * <p>
 * 状态机：0 草稿 → 1 已审核（单向）。审核时逐行过账库存（OTH_IN），
 * 过账走 {@link StockService}（行锁 + 流水 + 事务）。
 * <p>
 * 二期（V411）：行可带批次号 batchNo——批次物料入库走
 * {@link StockService#stockIn}（批次必填由其按物料标志强校验），
 * 非批次物料 batchNo 强制空串落汇总行，与一期行为一致。
 */
@Service
@RequiredArgsConstructor
public class InvOtherInServiceImpl implements InvOtherInService {

    private final InvOtherInMapper docMapper;
    private final InvOtherInLineMapper lineMapper;
    private final StockService stockService;
    private final BizNoGenerator bizNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OtherInForm form) {
        InvOtherIn doc = new InvOtherIn();
        doc.setNo(bizNoGenerator.next("OI"));
        doc.setWarehouseId(form.getWarehouseId());
        doc.setReason(form.getReason());
        doc.setRemark(form.getRemark());
        doc.setStatus(InvOtherIn.STATUS_DRAFT);
        docMapper.insert(doc);

        for (OtherInForm.Line line : form.getLines()) {
            InvOtherInLine entity = new InvOtherInLine();
            entity.setOtherInId(doc.getId());
            entity.setMaterialId(line.getMaterialId());
            entity.setBatchNo(line.getBatchNo() == null ? "" : line.getBatchNo().trim());
            entity.setExpiryDate(line.getExpiryDate());
            entity.setQty(line.getQty());
            entity.setRemark(line.getRemark());
            lineMapper.insert(entity);
        }
        return doc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InvOtherIn doc = requireDoc(id);
        if (doc.getStatus() != InvOtherIn.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.biz.doc-audited", doc.getNo());
        }
        List<InvOtherInLine> lines = lineMapper.selectList(
                Wrappers.lambdaQuery(InvOtherInLine.class).eq(InvOtherInLine::getOtherInId, id));
        if (lines.isEmpty()) {
            throw BusinessException.ofKey("erp.inv.doc-no-lines", doc.getNo());
        }
        // 逐行过账：加库存 + 落 OTH_IN 流水（与状态更新同一事务，失败整体回滚）。
        // 批次物料走批次入库（batchNo 必填由 StockService 校验，效期随行透传，V422）；
        // 非批次物料自动落汇总行。
        for (InvOtherInLine line : lines) {
            stockService.stockInAt(doc.getWarehouseId(), line.getMaterialId(), line.getBatchNo(),
                    null, line.getQty(), InvBizType.OTH_IN, doc.getId(), doc.getNo(),
                    doc.getReason(), line.getExpiryDate());
        }
        doc.setStatus(InvOtherIn.STATUS_AUDITED);
        doc.setAuditTime(LocalDateTime.now());
        docMapper.updateById(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InvOtherIn doc = requireDoc(id);
        if (doc.getStatus() != InvOtherIn.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.inv.doc-posted-no-delete", doc.getNo());
        }
        docMapper.deleteById(id);
        lineMapper.delete(Wrappers.lambdaQuery(InvOtherInLine.class)
                .eq(InvOtherInLine::getOtherInId, id));
    }

    @Override
    public PageResult<OtherDocVO> page(OtherDocQuery query) {
        IPage<OtherDocVO> page = docMapper.selectDocPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public OtherDocDetailVO detail(Long id) {
        InvOtherIn doc = requireDoc(id);
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

    private InvOtherIn requireDoc(Long id) {
        InvOtherIn doc = docMapper.selectById(id);
        if (doc == null) {
            throw BusinessException.ofKey("erp.inv.oth-in-not-exists", id);
        }
        return doc;
    }
}

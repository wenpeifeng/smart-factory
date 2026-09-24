package com.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.inventory.constant.InvBizType;
import com.erp.inventory.constant.OutStrategy;
import com.erp.inventory.dto.form.CheckActualForm;
import com.erp.inventory.dto.form.CheckCreateForm;
import com.erp.inventory.dto.request.CheckQuery;
import com.erp.inventory.dto.vo.CheckDetailVO;
import com.erp.inventory.dto.vo.CheckDocDetailVO;
import com.erp.inventory.dto.vo.CheckVO;
import com.erp.inventory.entity.InvCheck;
import com.erp.inventory.entity.InvCheckDetail;
import com.erp.inventory.mapper.InvCheckDetailMapper;
import com.erp.inventory.mapper.InvCheckMapper;
import com.erp.inventory.service.InvCheckService;
import com.erp.inventory.service.StockService;
import com.erp.inventory.support.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存盘点服务实现：快照 → 实盘 → 差异调平。
 * <p>
 * 关键点：
 * <ul>
 *   <li>快照用 INSERT...SELECT 原子抄库（inv_stock → inv_check_detail.book_qty）；</li>
 *   <li>实盘可分批保存，diff_qty = actual_qty - book_qty 实时计算；</li>
 *   <li>完成时对 diff != 0 的行生成 ADJUST 流水调平（盘盈正入/盘亏负出），
 *       与单据状态更新同一事务；未录实盘的行视为未盘点，不调平。</li>
 *   <li>二期（V411）：快照与调平均带批次维度——批次行差异按批次调平
 *       （盘盈 stockIn 到该批次 / 盘亏 stockOut 指定批次），
 *       非批次行（batch_no=''）仍走 changeStock 汇总行，与一期一致。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class InvCheckServiceImpl implements InvCheckService {

    private final InvCheckMapper checkMapper;
    private final InvCheckDetailMapper detailMapper;
    private final StockService stockService;
    private final BizNoGenerator bizNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CheckCreateForm form) {
        return createWithSource(form.getWarehouseId(), InvCheck.SOURCE_MANUAL, form.getRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWithSource(Long warehouseId, String source, String remark) {
        InvCheck check = new InvCheck();
        check.setNo(bizNoGenerator.next("CK"));
        check.setWarehouseId(warehouseId);
        check.setSource(source == null || source.isBlank() ? InvCheck.SOURCE_MANUAL : source);
        check.setRemark(remark);
        check.setStatus(InvCheck.STATUS_OPEN);
        checkMapper.insert(check);

        // 库存快照：该仓库当前全部库存行抄入明细（book_qty，含批次+库位），原子 INSERT...SELECT
        detailMapper.snapshotFromStock(check.getId(), warehouseId);
        return check.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveActual(Long id, CheckActualForm form) {
        InvCheck check = requireCheck(id);
        requireOpen(check);

        // 一次查出该单全部明细，校验归属并避免逐行 SELECT
        Map<Long, InvCheckDetail> detailMap = new HashMap<>();
        for (InvCheckDetail d : detailMapper.selectList(
                Wrappers.lambdaQuery(InvCheckDetail.class).eq(InvCheckDetail::getCheckId, id))) {
            detailMap.put(d.getId(), d);
        }
        for (CheckActualForm.Line line : form.getLines()) {
            InvCheckDetail detail = detailMap.get(line.getDetailId());
            if (detail == null) {
                throw BusinessException.ofKey("erp.inv.stocktake-line-not-mine", line.getDetailId(), check.getNo());
            }
            detail.setActualQty(line.getActualQty());
            detail.setDiffQty(line.getActualQty().subtract(detail.getBookQty()));
            detailMapper.updateById(detail);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int complete(Long id) {
        InvCheck check = requireCheck(id);
        requireOpen(check);

        List<InvCheckDetail> details = detailMapper.selectList(
                Wrappers.lambdaQuery(InvCheckDetail.class).eq(InvCheckDetail::getCheckId, id));
        int adjusted = 0;
        for (InvCheckDetail detail : details) {
            if (detail.getActualQty() == null) {
                continue; // 未录实盘 = 未盘点，不调平
            }
            if (detail.getDiffQty() == null || detail.getDiffQty().signum() == 0) {
                continue; // 账实相符
            }
            String remark = "盘点调平 账面" + detail.getBookQty().stripTrailingZeros().toPlainString()
                    + " 实盘" + detail.getActualQty().stripTrailingZeros().toPlainString();
            boolean batchRow = detail.getBatchNo() != null && !detail.getBatchNo().isBlank();
            // 差异调平：盘盈(+diff)入库 / 盘亏(-diff)出库，统一 ADJUST 流水。
            // V421 起按快照行的 (批次, 库位) 精确调平：库位行用库位维度原语，
            // 未上架行（location=0）保持原逻辑（批次行走 stockIn/BATCH、非批次走汇总行）。
            Long locationId = detail.getLocationId() == null ? 0L : detail.getLocationId();
            if (locationId > 0) {
                if (detail.getDiffQty().signum() > 0) {
                    stockService.stockInAt(check.getWarehouseId(), detail.getMaterialId(),
                            detail.getBatchNo(), locationId, detail.getDiffQty(),
                            InvBizType.ADJUST, check.getId(), check.getNo(), remark, null);
                } else {
                    stockService.stockOutAtLocation(check.getWarehouseId(), detail.getMaterialId(),
                            detail.getBatchNo(), locationId, detail.getDiffQty().negate(),
                            InvBizType.ADJUST, check.getId(), check.getNo(), remark);
                }
            } else if (batchRow) {
                if (detail.getDiffQty().signum() > 0) {
                    stockService.stockIn(check.getWarehouseId(), detail.getMaterialId(),
                            detail.getBatchNo(), detail.getDiffQty(),
                            InvBizType.ADJUST, check.getId(), check.getNo(), remark);
                } else {
                    stockService.stockOut(check.getWarehouseId(), detail.getMaterialId(),
                            detail.getDiffQty().negate(), OutStrategy.BATCH, detail.getBatchNo(),
                            InvBizType.ADJUST, check.getId(), check.getNo(), remark);
                }
            } else {
                stockService.changeStock(check.getWarehouseId(), detail.getMaterialId(),
                        detail.getDiffQty(), InvBizType.ADJUST, check.getId(), check.getNo(), remark);
            }
            adjusted++;
        }
        check.setStatus(InvCheck.STATUS_DONE);
        check.setCompleteTime(LocalDateTime.now());
        checkMapper.updateById(check);
        return adjusted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InvCheck check = requireCheck(id);
        if (check.getStatus() != InvCheck.STATUS_OPEN) {
            throw BusinessException.ofKey("erp.inv.stocktake-balanced-no-delete", check.getNo());
        }
        checkMapper.deleteById(id);
        detailMapper.delete(Wrappers.lambdaQuery(InvCheckDetail.class)
                .eq(InvCheckDetail::getCheckId, id));
    }

    @Override
    public PageResult<CheckVO> page(CheckQuery query) {
        IPage<CheckVO> page = checkMapper.selectCheckPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public CheckDocDetailVO detail(Long id) {
        InvCheck check = requireCheck(id);
        CheckDocDetailVO vo = new CheckDocDetailVO();
        vo.setId(check.getId());
        vo.setNo(check.getNo());
        vo.setWarehouseId(check.getWarehouseId());
        vo.setStatus(check.getStatus());
        vo.setSource(check.getSource());
        vo.setCompleteTime(check.getCompleteTime());
        vo.setRemark(check.getRemark());
        vo.setCreateTime(check.getCreateTime());
        List<CheckDetailVO> lines = detailMapper.selectDetailsByCheckId(id);
        vo.setLines(lines);
        return vo;
    }

    private InvCheck requireCheck(Long id) {
        InvCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw BusinessException.ofKey("erp.inv.stocktake-not-exists", id);
        }
        return check;
    }

    private void requireOpen(InvCheck check) {
        if (check.getStatus() != InvCheck.STATUS_OPEN) {
            throw BusinessException.ofKey("erp.inv.stocktake-done", check.getNo());
        }
    }
}

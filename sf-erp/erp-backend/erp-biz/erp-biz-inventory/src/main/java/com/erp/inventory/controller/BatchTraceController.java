package com.erp.inventory.controller;

import com.erp.common.result.Result;
import com.erp.inventory.dto.vo.BatchStockVO;
import com.erp.inventory.dto.vo.TraceNodeVO;
import com.erp.inventory.mapper.InvMovementMapper;
import com.erp.inventory.mapper.InvStockMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 批次管理接口（V411 二期）：批次余额清单 + 批次正溯/反溯。
 * <p>
 * 追溯只基于 inv_movement 的 biz_type/biz_no（不 JOIN 采购/销售等业务实体），
 * 来源/去向单据详情由前端拿 bizNo 跳转对应模块查看。
 */
@Tag(name = "库存管理-批次追溯")
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class BatchTraceController {

    private final InvStockMapper stockMapper;
    private final InvMovementMapper movementMapper;

    /** 批次余额清单：某物料（可按仓库过滤）全部批次行，含数量/首入时间/库龄天，按首入时间升序（FIFO 顺序） */
    @Operation(summary = "批次余额清单")
    @RequirePerm("inventory:batch:list")
    @GetMapping("/batches")
    public Result<List<BatchStockVO>> batches(@RequestParam Long materialId,
                                              @RequestParam(required = false) Long warehouseId) {
        return Result.ok(stockMapper.selectBatchList(materialId, warehouseId));
    }

    /** 批次正溯：该批次出库流向（出库单/调拨/领用单号），按时间正序 */
    @Operation(summary = "批次正溯(流向查询)")
    @RequirePerm("inventory:trace:list")
    @GetMapping("/trace/forward")
    public Result<List<TraceNodeVO>> forwardTrace(@RequestParam Long materialId,
                                                  @RequestParam String batchNo) {
        return Result.ok(movementMapper.selectForwardTrace(materialId, batchNo));
    }

    /** 批次反溯：该批次的入库来源（入库单/采购单号），按时间正序 */
    @Operation(summary = "批次反溯(来源查询)")
    @RequirePerm("inventory:trace:list")
    @GetMapping("/trace/backward")
    public Result<List<TraceNodeVO>> backwardTrace(@RequestParam Long materialId,
                                                   @RequestParam String batchNo) {
        return Result.ok(movementMapper.selectBackwardTrace(materialId, batchNo));
    }
}

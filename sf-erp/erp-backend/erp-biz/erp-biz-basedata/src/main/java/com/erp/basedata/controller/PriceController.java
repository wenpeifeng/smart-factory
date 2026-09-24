package com.erp.basedata.controller;

import com.erp.basedata.dto.form.PriceRecordForm;
import com.erp.basedata.dto.vo.PriceCompareRowVO;
import com.erp.basedata.dto.vo.PriceRecordVO;
import com.erp.basedata.dto.vo.PriceRefVO;
import com.erp.basedata.service.PurPriceService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购价格库接口（价格流水 + 录单参照）。
 * <p>
 * last/history/compare 为录单参照接口（登录即可，供采购/销售录单调参照）；
 * 流水维护（补录/删除）与分页按 basedata:price:* 授权。
 */
@Tag(name = "基础资料-采购价格库")
@RestController
@RequestMapping("/basedata/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PurPriceService purPriceService;

    /** 价格流水分页（价格中心-采购价格流水 Tab） */
    @Operation(summary = "价格流水分页")
    @RequirePerm("basedata:price:list")
    @GetMapping("/records")
    public Result<PageResult<PriceRecordVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String sourceType) {
        return Result.ok(purPriceService.page(pageNum, pageSize, supplierId, materialId, sourceType));
    }

    /** 录单参照：合同价优先 + 最近一次成交价 */
    @Operation(summary = "最近价参照(合同价优先)")
    @GetMapping("/last")
    public Result<PriceRefVO> lastRef(
            @RequestParam Long supplierId,
            @RequestParam Long materialId) {
        return Result.ok(purPriceService.lastRef(supplierId, materialId));
    }

    /** 历史价（日期倒序，含来源单号可追溯；supplierId 可空=全部供应商） */
    @Operation(summary = "历史价分页")
    @GetMapping("/history")
    public Result<PageResult<PriceRecordVO>> history(
            @RequestParam Long materialId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "3") long pageSize) {
        return Result.ok(purPriceService.history(materialId, supplierId, pageNum, pageSize));
    }

    /** 多供应商同物料价格对比（最低/最高/均价/最近价） */
    @Operation(summary = "多供应商价格对比")
    @GetMapping("/compare")
    public Result<List<PriceCompareRowVO>> compare(@RequestParam Long materialId) {
        return Result.ok(purPriceService.compare(materialId));
    }

    /** 手工补录价格流水（历史导入/合同价） */
    @Operation(summary = "手工补录价格流水")
    @RequirePerm("basedata:price:add")
    @OperLog(module = "价格管理", operation = "补录采购价格流水")
    @Idempotent
    @PostMapping("/records")
    public Result<Long> create(@Valid @RequestBody PriceRecordForm form) {
        return Result.ok(purPriceService.createManual(form));
    }

    /** 删除价格流水（逻辑删除，扫描器不会重复沉淀） */
    @Operation(summary = "删除价格流水")
    @RequirePerm("basedata:price:delete")
    @OperLog(module = "价格管理", operation = "删除采购价格流水")
    @Idempotent
    @DeleteMapping("/records/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purPriceService.delete(id);
        return Result.ok();
    }
}

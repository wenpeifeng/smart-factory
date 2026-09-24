package com.erp.hr.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.hr.dto.form.PiecePriceForm;
import com.erp.hr.dto.vo.PiecePriceVO;
import com.erp.hr.service.PiecePriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 计件价目接口：物料×工序单价的 CRUD。
 */
@Tag(name = "人力资源-计件价目")
@RestController
@RequestMapping("/hr/piece-prices")
@RequiredArgsConstructor
public class HrPiecePriceController {

    private final PiecePriceService priceService;

    /** 分页查询价目（keyword 匹配物料编码/名称或工序名） */
    @Operation(summary = "分页查询计件价目")
    @RequirePerm("hr:piecewage:list")
    @GetMapping
    public Result<PageResult<PiecePriceVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(priceService.page(pageNum, pageSize, keyword, status));
    }

    /** 价目详情 */
    @Operation(summary = "计件价目详情")
    @RequirePerm("hr:piecewage:list")
    @GetMapping("/{id}")
    public Result<PiecePriceVO> detail(@PathVariable Long id) {
        return Result.ok(priceService.detail(id));
    }

    /** 新增价目（同物料同工序唯一） */
    @Operation(summary = "新增计件价目")
    @RequirePerm("hr:piecewage:add")
    @OperLog(module = "人力资源", operation = "新增计件价格")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PiecePriceForm form) {
        return Result.ok(priceService.create(form));
    }

    /** 修改价目 */
    @Operation(summary = "修改计件价目")
    @RequirePerm("hr:piecewage:edit")
    @OperLog(module = "人力资源", operation = "修改计件价格")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PiecePriceForm form) {
        priceService.update(id, form);
        return Result.ok();
    }

    /** 删除价目（逻辑删除） */
    @Operation(summary = "删除计件价目")
    @RequirePerm("hr:piecewage:delete")
    @OperLog(module = "人力资源", operation = "删除计件价格")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        priceService.delete(id);
        return Result.ok();
    }
}

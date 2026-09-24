package com.erp.asset.controller;

import com.erp.asset.dto.form.AssetForm;
import com.erp.asset.dto.form.DisposeForm;
import com.erp.asset.dto.request.AssetQuery;
import com.erp.asset.dto.vo.AssetVO;
import com.erp.asset.dto.vo.DisposeResultVO;
import com.erp.asset.service.AstAssetService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
 * 资产卡片管理接口：CRUD + 入账/处置自动凭证。
 * <p>
 * 出参一律 {@link AssetVO}；路径 id 为准，覆盖表单内 id。
 */
@Tag(name = "资产卡片")
@RestController
@RequestMapping("/asset/assets")
@RequiredArgsConstructor
public class AstAssetController {

    private final AstAssetService assetService;

    /** 资产分页查询（类别/状态/部门过滤，编码/名称模糊） */
    @Operation(summary = "资产分页查询")
    @RequirePerm("asset:asset:list")
    @GetMapping
    public Result<PageResult<AssetVO>> page(@ModelAttribute AssetQuery query,
                                            @RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(assetService.page(query, pageNum, pageSize));
    }

    /** 资产详情 */
    @Operation(summary = "资产详情")
    @RequirePerm("asset:asset:list")
    @GetMapping("/{id}")
    public Result<AssetVO> detail(@PathVariable Long id) {
        return Result.ok(assetService.detail(id));
    }

    /** 新增资产（自动生成 ZC 编码 + 入账凭证） */
    @Operation(summary = "新增资产")
    @RequirePerm("asset:asset:add")
    @OperLog(module = "资产管理", operation = "新增资产")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AssetForm form) {
        return Result.ok(assetService.create(form));
    }

    /** 修改资产（财务字段计提后锁定；状态仅 在用/闲置 互转） */
    @Operation(summary = "修改资产")
    @RequirePerm("asset:asset:edit")
    @OperLog(module = "资产管理", operation = "修改资产")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AssetForm form) {
        assetService.update(id, form);
        return Result.ok();
    }

    /** 删除资产（已计提/已处置拒绝） */
    @Operation(summary = "删除资产")
    @RequirePerm("asset:asset:delete")
    @OperLog(module = "资产管理", operation = "删除资产")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return Result.ok();
    }

    /** 资产处置（净值转固定资产清理，自动平衡凭证，状态→处置） */
    @Operation(summary = "资产处置")
    @RequirePerm("asset:asset:audit")
    @OperLog(module = "资产管理", operation = "资产处置")
    @Idempotent
    @PostMapping("/{id}/dispose")
    public Result<DisposeResultVO> dispose(@PathVariable Long id, @Valid @RequestBody DisposeForm form) {
        return Result.ok(assetService.dispose(id, form));
    }
}

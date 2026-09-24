package com.mes.basedata.controller;

import com.mes.basedata.dto.form.BaseMaterialForm;
import com.mes.basedata.dto.query.MaterialPageQuery;
import com.mes.basedata.dto.vo.BaseMaterialVO;
import com.mes.basedata.dto.vo.MaterialImportResultVO;
import com.mes.basedata.service.BaseMaterialService;
import com.mes.common.result.PageResult;
import com.mes.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.mes.framework.idempotent.Idempotent;
import com.mes.framework.security.RequirePerm;
import com.mes.system.annotation.OperLog;

/**
 * 物料接口：分页/CRUD/启停用/CSV 导入导出。
 */
@Tag(name = "基础资料-物料")
@RestController
@RequestMapping("/basedata/materials")
@RequiredArgsConstructor
public class BaseMaterialController {

    private final BaseMaterialService materialService;

    /** 分页查询（编码/名称关键词 + 分类含子孙 + 状态） */
    @Operation(summary = "分页查询物料")
    @RequirePerm("basedata:material:list")
    @GetMapping
    public Result<PageResult<BaseMaterialVO>> page(MaterialPageQuery query) {
        return Result.ok(materialService.page(query));
    }

    /** 启用物料搜索（单据选择器下拉） */
    @Operation(summary = "启用物料搜索(下拉)")
    @GetMapping("/enabled")
    public Result<List<BaseMaterialVO>> listEnabled(@RequestParam(required = false) String keyword) {
        return Result.ok(materialService.listEnabled(keyword));
    }

    /** 物料详情 */
    @Operation(summary = "物料详情")
    @RequirePerm("basedata:material:list")
    @GetMapping("/{id}")
    public Result<BaseMaterialVO> getById(@PathVariable Long id) {
        return Result.ok(materialService.getById(id));
    }

    /** 新增物料（编码留空自动生成） */
    @Operation(summary = "新增物料")
    @RequirePerm("basedata:material:add")
    @OperLog(module = "物料管理", operation = "新增物料")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BaseMaterialForm form) {
        return Result.ok(materialService.create(form));
    }

    /** 修改物料 */
    @Operation(summary = "修改物料")
    @RequirePerm("basedata:material:edit")
    @OperLog(module = "物料管理", operation = "修改物料")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BaseMaterialForm form) {
        materialService.update(id, form);
        return Result.ok();
    }

    /** 启用/停用物料 */
    @Operation(summary = "启用停用物料")
    @RequirePerm("basedata:material:edit")
    @OperLog(module = "物料管理", operation = "启停物料")
    @Idempotent
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        materialService.changeStatus(id, status);
        return Result.ok();
    }

    /** 删除物料 */
    @Operation(summary = "删除物料")
    @RequirePerm("basedata:material:delete")
    @OperLog(module = "物料管理", operation = "删除物料")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.ok();
    }

    /** 按当前筛选条件导出 CSV */
    @Operation(summary = "导出物料CSV")
    @RequirePerm("basedata:material:export")
    @GetMapping("/export")
    public void exportCsv(MaterialPageQuery query, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=%s".formatted(
                URLEncoder.encode("materials_" + System.currentTimeMillis() + ".csv", StandardCharsets.UTF_8)));
        materialService.exportCsv(query, response.getWriter());
    }

    /** 下载导入模板 CSV */
    @Operation(summary = "下载导入模板")
    @RequirePerm("basedata:material:add")
    @GetMapping("/import-template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=%s".formatted(
                URLEncoder.encode("materials_template.csv", StandardCharsets.UTF_8)));
        materialService.downloadTemplate(response.getWriter());
    }

    /** 上传 CSV 导入（逐行校验、错误跳过） */
    @Operation(summary = "导入物料CSV")
    @RequirePerm("basedata:material:add")
    @OperLog(module = "物料管理", operation = "导入物料")
    @Idempotent
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MaterialImportResultVO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.ok(materialService.importCsv(file));
    }
}

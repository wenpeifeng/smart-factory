package com.erp.finance.controller;

import com.erp.common.result.Result;
import com.erp.finance.dto.form.VoucherTemplateForm;
import com.erp.finance.dto.vo.VoucherTemplateVO;
import com.erp.finance.service.VoucherTemplateService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 凭证模板管理接口（V521）：八类业务单据 → 会计凭证分录规则 CRUD。
 * <p>
 * 模板全局共享（不按账套隔离），科目编码在生成时按目标账套解析。
 */
@Tag(name = "财务管理-凭证模板")
@RestController
@RequestMapping("/finance/voucher-templates")
@RequiredArgsConstructor
public class VoucherTemplateController {

    private final VoucherTemplateService templateService;

    @Operation(summary = "模板列表（全部八类，含停用）")
    @RequirePerm("finance:voucherTemplate:list")
    @GetMapping
    public Result<List<VoucherTemplateVO>> list() {
        return Result.ok(templateService.list());
    }

    @Operation(summary = "新增模板（每类单据一条，uk(biz_type)）")
    @RequirePerm("finance:voucherTemplate:add")
    @OperLog(module = "财务管理", operation = "新增凭证模板")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody VoucherTemplateForm form) {
        return Result.ok(templateService.create(form));
    }

    @Operation(summary = "修改模板（单据类型不可改；enabled=0 停用后扫描器不再生成）")
    @RequirePerm("finance:voucherTemplate:edit")
    @OperLog(module = "财务管理", operation = "修改凭证模板")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody VoucherTemplateForm form) {
        templateService.update(id, form);
        return Result.ok();
    }

    @Operation(summary = "删除模板（逻辑删；建议以停用替代保留追溯）")
    @RequirePerm("finance:voucherTemplate:delete")
    @OperLog(module = "财务管理", operation = "删除凭证模板")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.ok();
    }
}

package com.erp.finance.controller;

import com.erp.common.result.Result;
import com.erp.finance.dto.form.SubjectForm;
import com.erp.finance.dto.vo.SubjectVO;
import com.erp.finance.service.FinSubjectService;
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

import java.util.List;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 会计科目接口：科目树维护（增改删查）。
 */
@Tag(name = "财务管理-会计科目")
@RestController
@RequestMapping("/finance/subjects")
@RequiredArgsConstructor
public class FinSubjectController {

    private final FinSubjectService subjectService;

    @Operation(summary = "科目列表（按编码升序，含末级标识）")
    @RequirePerm("finance:subject:list")
    @GetMapping
    public Result<List<SubjectVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(subjectService.list(keyword));
    }

    @Operation(summary = "新增科目")
    @RequirePerm("finance:subject:add")
    @OperLog(module = "财务管理", operation = "新增科目")
    @Idempotent
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SubjectForm form) {
        subjectService.create(form);
        return Result.ok();
    }

    @Operation(summary = "修改科目")
    @RequirePerm("finance:subject:edit")
    @OperLog(module = "财务管理", operation = "修改科目")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SubjectForm form) {
        subjectService.update(id, form);
        return Result.ok();
    }

    @Operation(summary = "删除科目（有下级或被分录引用时拒绝）")
    @RequirePerm("finance:subject:delete")
    @OperLog(module = "财务管理", operation = "删除科目")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return Result.ok();
    }
}

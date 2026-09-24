package com.erp.finance.controller;

import com.erp.common.result.Result;
import com.erp.finance.dto.form.BookForm;
import com.erp.finance.dto.vo.BookVO;
import com.erp.finance.service.FinBookService;
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
 * 账套管理接口：多账套 CRUD + 新账套科目树复制。
 * <p>
 * 账套切换不走本接口：请求头 {@code X-Book-Id} 指定当前账套（缺省 1001），
 * 响应头回显实际生效账套（见 BookContextInterceptor）。
 */
@Tag(name = "财务管理-账套管理")
@RestController
@RequestMapping("/finance/books")
@RequiredArgsConstructor
public class FinBookController {

    private final FinBookService bookService;

    @Operation(summary = "账套列表（含默认账套标记）")
    @RequirePerm("finance:book:list")
    @GetMapping
    public Result<List<BookVO>> list() {
        return Result.ok(bookService.list());
    }

    @Operation(summary = "新增账套（编码 BS+流水自动生成）")
    @RequirePerm("finance:book:add")
    @OperLog(module = "财务管理", operation = "新增账簿")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BookForm form) {
        return Result.ok(bookService.create(form));
    }

    @Operation(summary = "修改账套（名称/期间政策/本位币/状态/备注）")
    @RequirePerm("finance:book:edit")
    @OperLog(module = "财务管理", operation = "修改账簿")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BookForm form) {
        bookService.update(id, form);
        return Result.ok();
    }

    @Operation(summary = "删除账套（默认账套/已有凭证的账套拒绝）")
    @RequirePerm("finance:book:delete")
    @OperLog(module = "财务管理", operation = "删除账簿")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "复制科目树到本账套（新账套初始化，幂等：同编码跳过）")
    @RequirePerm("finance:book:add")
    @OperLog(module = "财务管理", operation = "复制科目到账簿")
    @Idempotent
    @PostMapping("/{id}/copy-subjects")
    public Result<Integer> copySubjects(@PathVariable Long id,
                                        @RequestParam(required = false) Long fromBookId) {
        return Result.ok(bookService.copySubjects(id, fromBookId));
    }
}

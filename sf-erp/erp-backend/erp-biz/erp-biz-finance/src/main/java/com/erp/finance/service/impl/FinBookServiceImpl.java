package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.finance.context.BookContext;
import com.erp.finance.dto.form.BookForm;
import com.erp.finance.dto.vo.BookVO;
import com.erp.finance.entity.FinBook;
import com.erp.finance.entity.FinSubject;
import com.erp.finance.entity.FinVoucher;
import com.erp.finance.mapper.FinBookMapper;
import com.erp.finance.mapper.FinSubjectMapper;
import com.erp.finance.mapper.FinVoucherMapper;
import com.erp.finance.service.FinBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 账套档案服务实现。
 * <p>
 * 删除保护：默认账套与已有凭证的账套禁删（对标金蝶"账套启用后不可物理删除"）；
 * 科目复制按层级逐级进行并重挂 parent_id，任意深度科目树均正确、可重复执行。
 */
@Service
@RequiredArgsConstructor
public class FinBookServiceImpl implements FinBookService {

    /** 会计期间政策默认值：自然月 */
    private static final String DEFAULT_PERIOD_POLICY = "NATURAL_MONTH";
    /** 本位币默认值：人民币 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final FinBookMapper bookMapper;
    private final FinSubjectMapper subjectMapper;
    private final FinVoucherMapper voucherMapper;

    @Override
    public List<BookVO> list() {
        return bookMapper.selectList(Wrappers.lambdaQuery(FinBook.class)
                        .orderByAsc(FinBook::getId))
                .stream().map(BookVO::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(BookForm form) {
        checkNameUnique(form.name(), null);
        FinBook book = new FinBook();
        // 账套是租户级共享档案：org 显式置 0（MP 严格填充只在 null 时生效，显式值优先）；
        // tenant_id 由审计填充器按登录上下文自动写入（无上下文默认 1000）
        book.setOrgId(0L);
        // 先用临时编码占位（code 非空且不超 VARCHAR(20)，用纳秒十六进制保证极短且不撞），
        // 插入拿到自增 ID 后回写 BS+ID，天然不重复
        book.setCode("BS#" + Long.toHexString(System.nanoTime()));
        applyForm(book, form);
        book.setIsDefault(0);
        bookMapper.insert(book);
        book.setCode("BS" + book.getId());
        bookMapper.updateById(book);
        return book.getId();
    }

    @Override
    public void update(Long id, BookForm form) {
        FinBook book = requireBook(id);
        checkNameUnique(form.name(), id);
        applyForm(book, form);
        bookMapper.updateById(book);
    }

    @Override
    public void delete(Long id) {
        FinBook book = requireBook(id);
        if (book.getIsDefault() != null && book.getIsDefault() == 1) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.fin.default-book-no-delete", book.getName());
        }
        Long vouchers = voucherMapper.selectCount(Wrappers.lambdaQuery(FinVoucher.class)
                .eq(FinVoucher::getBookId, id));
        if (vouchers > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.fin.book-has-vouchers-no-delete", book.getName(), vouchers);
        }
        bookMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer copySubjects(Long toBookId, Long fromBookId) {
        long target = toBookId == null ? BookContext.getBookId() : toBookId;
        long source = fromBookId == null ? BookContext.DEFAULT_BOOK_ID : fromBookId;
        if (target == source) {
            throw BusinessException.ofKey("erp.fin.same-book");
        }
        requireBook(target);
        requireBook(source);

        List<FinSubject> sources = subjectMapper.selectList(Wrappers.lambdaQuery(FinSubject.class)
                .eq(FinSubject::getBookId, source)
                .orderByAsc(FinSubject::getCode));
        if (sources.isEmpty()) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.source-book-no-subjects", source);
        }

        // 目标账套已有科目：编码 → 实体（幂等基础 + 建立源/目标 ID 映射）
        Map<String, FinSubject> existing = subjectMapper.selectList(
                        Wrappers.lambdaQuery(FinSubject.class).eq(FinSubject::getBookId, target))
                .stream().collect(Collectors.toMap(FinSubject::getCode, s -> s));

        // 源科目 ID → 目标科目 ID 映射（父级重挂用）
        Map<Long, Long> idMapping = new HashMap<>();
        int copied = 0;
        // 按层级复制：每轮只复制"父级已映射（或本就一级）"的科目，直至无新增
        List<FinSubject> remaining = new java.util.ArrayList<>(sources);
        while (!remaining.isEmpty()) {
            List<FinSubject> nextRound = new java.util.ArrayList<>();
            for (FinSubject s : remaining) {
                if (existing.containsKey(s.getCode())) {
                    // 已有同编码科目：仅建立映射（支持部分复制后补）
                    idMapping.put(s.getId(), existing.get(s.getCode()).getId());
                    continue;
                }
                Long targetParentId = 0L;
                if (s.getParentId() != null && s.getParentId() != 0L) {
                    Long mapped = idMapping.get(s.getParentId());
                    if (mapped == null) {
                        // 父级尚未复制（父级编码在目标账套缺失）→ 下一轮再处理
                        nextRound.add(s);
                        continue;
                    }
                    targetParentId = mapped;
                }
                FinSubject copy = new FinSubject();
                copy.setBookId(target);
                copy.setCode(s.getCode());
                copy.setName(s.getName());
                copy.setParentId(targetParentId);
                copy.setCategory(s.getCategory());
                copy.setDirection(s.getDirection());
                copy.setStatus(s.getStatus());
                copy.setRemark(s.getRemark());
                subjectMapper.insert(copy);
                idMapping.put(s.getId(), copy.getId());
                existing.put(copy.getCode(), copy);
                copied++;
            }
            // 一轮下来没有任何进展（源数据父链断裂/成环）则终止，避免死循环
            if (nextRound.size() == remaining.size()) {
                throw BusinessException.ofKey("erp.fin.subject-tree-broken");
            }
            remaining = nextRound;
        }
        return copied;
    }

    /** 账套必须存在 */
    private FinBook requireBook(Long id) {
        FinBook book = bookMapper.selectById(id);
        if (book == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.book-not-exists", id);
        }
        return book;
    }

    /** 账套名称唯一性校验（编码系统生成，无需校验） */
    private void checkNameUnique(String name, Long excludeId) {
        Long count = bookMapper.selectCount(Wrappers.lambdaQuery(FinBook.class)
                .eq(FinBook::getName, name)
                .ne(excludeId != null, FinBook::getId, excludeId));
        if (count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.fin.book-name-exists", name);
        }
    }

    /** 表单字段拷贝（不动 code / isDefault） */
    private void applyForm(FinBook book, BookForm form) {
        book.setName(form.name());
        book.setPeriodPolicy(form.periodPolicy() == null || form.periodPolicy().isBlank()
                ? DEFAULT_PERIOD_POLICY : form.periodPolicy());
        book.setCurrency(form.currency() == null || form.currency().isBlank()
                ? DEFAULT_CURRENCY : form.currency());
        book.setStatus(form.status() == null ? 1 : form.status());
        book.setRemark(form.remark());
    }
}

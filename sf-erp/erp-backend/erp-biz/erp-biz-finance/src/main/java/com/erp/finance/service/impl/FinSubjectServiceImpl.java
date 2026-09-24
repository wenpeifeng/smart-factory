package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.finance.context.BookContext;
import com.erp.finance.dto.form.SubjectForm;
import com.erp.finance.dto.vo.SubjectVO;
import com.erp.finance.entity.FinSubject;
import com.erp.finance.entity.FinVoucherEntry;
import com.erp.finance.mapper.FinSubjectMapper;
import com.erp.finance.mapper.FinVoucherEntryMapper;
import com.erp.finance.service.FinSubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会计科目服务实现。
 * <p>
 * 科目树通过 parent_id 组织（0 = 一级）；leaf（末级）判定依据是否存在下级科目。
 * 多账套：科目树按账套完全隔离——查询/写入统一带当前账套（X-Book-Id，缺省 1001），
 * 编码唯一性按账套校验；跨账套 ID 访问视同不存在。
 */
@Service
@RequiredArgsConstructor
public class FinSubjectServiceImpl implements FinSubjectService {

    private final FinSubjectMapper subjectMapper;
    private final FinVoucherEntryMapper voucherEntryMapper;

    @Override
    public List<SubjectVO> list(String keyword) {
        List<FinSubject> subjects = subjectMapper.selectList(Wrappers.lambdaQuery(FinSubject.class)
                .eq(FinSubject::getBookId, BookContext.getBookId())
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(FinSubject::getCode, keyword).or().like(FinSubject::getName, keyword))
                .orderByAsc(FinSubject::getCode));
        // 有下级的科目 ID 集合 → 不在集合中的即末级科目
        Set<Long> parentIds = subjects.stream().map(FinSubject::getParentId).collect(Collectors.toSet());
        return subjects.stream().map(s -> SubjectVO.from(s, !parentIds.contains(s.getId()))).toList();
    }

    @Override
    public void create(SubjectForm form) {
        checkCodeUnique(form.code(), null);
        Long parentId = normalizeParent(form.parentId());
        FinSubject subject = new FinSubject();
        applyForm(subject, form, parentId);
        subject.setBookId(BookContext.getBookId());
        subjectMapper.insert(subject);
    }

    @Override
    public void update(Long id, SubjectForm form) {
        FinSubject subject = requireSubject(id);
        checkCodeUnique(form.code(), id);
        Long parentId = normalizeParent(form.parentId());
        if (parentId.equals(id)) {
            throw BusinessException.ofKey("erp.fin.parent-subject-self");
        }
        // 防止把科目挂到自己的子孙级形成环：从新上级逐级上溯，遇到自身即非法
        Long cursor = parentId;
        while (cursor != 0L) {
            if (cursor.equals(id)) {
                throw BusinessException.ofKey("erp.fin.parent-subject-descendant");
            }
            FinSubject parent = requireSubject(cursor);
            cursor = parent.getParentId();
        }
        applyForm(subject, form, parentId);
        subjectMapper.updateById(subject);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireSubject(id);
        Long children = subjectMapper.selectCount(Wrappers.lambdaQuery(FinSubject.class)
                .eq(FinSubject::getBookId, BookContext.getBookId())
                .eq(FinSubject::getParentId, id));
        if (children > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.fin.subject-has-children");
        }
        Long used = voucherEntryMapper.selectCount(Wrappers.lambdaQuery(FinVoucherEntry.class)
                .eq(FinVoucherEntry::getBookId, BookContext.getBookId())
                .eq(FinVoucherEntry::getSubjectId, id));
        if (used > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.fin.subject-referenced-no-delete");
        }
        subjectMapper.deleteById(id);
    }

    /** 科目必须存在且属于当前账套 */
    private FinSubject requireSubject(Long id) {
        FinSubject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.subject-not-exists", id);
        }
        BookContext.checkBookOwnership(subject.getBookId(), "科目[%s %s]".formatted(subject.getCode(), subject.getName()));
        return subject;
    }

    /** 编码唯一性校验（账套内唯一；excludeId 用于修改场景排除自身） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = subjectMapper.selectCount(Wrappers.lambdaQuery(FinSubject.class)
                .eq(FinSubject::getBookId, BookContext.getBookId())
                .eq(FinSubject::getCode, code)
                .ne(excludeId != null, FinSubject::getId, excludeId));
        if (count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.fin.subject-code-exists", code);
        }
    }

    /** 上级科目归一化：null → 0（一级）；非 0 时必须存在且属于当前账套 */
    private Long normalizeParent(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return 0L;
        }
        requireSubject(parentId);
        return parentId;
    }

    /** 表单字段拷贝到实体（不动 bookId） */
    private void applyForm(FinSubject subject, SubjectForm form, Long parentId) {
        subject.setCode(form.code());
        subject.setName(form.name());
        subject.setParentId(parentId);
        subject.setCategory(form.category());
        subject.setDirection(form.direction());
        subject.setStatus(form.status() == null ? 1 : form.status());
        subject.setRemark(form.remark());
    }
}

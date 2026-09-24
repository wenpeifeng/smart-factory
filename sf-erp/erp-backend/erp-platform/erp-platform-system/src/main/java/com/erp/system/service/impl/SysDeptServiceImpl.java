package com.erp.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.system.dto.dept.DeptForm;
import com.erp.system.dto.dept.DeptVO;
import com.erp.system.entity.SysDept;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门管理服务实现（树形，parent_id 自关联）。
 * <p>
 * 删除保护：存在下级部门或挂靠用户时拒绝删除；
 * 修改保护：上级不能选自己或自己的后代（防环）。
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl implements SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    @Override
    public List<DeptVO> tree(String keyword) {
        List<SysDept> depts = deptMapper.selectList(Wrappers.lambdaQuery(SysDept.class)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysDept::getName, keyword.trim()))
                .orderByAsc(SysDept::getSort).orderByAsc(SysDept::getId));
        List<DeptVO> vos = depts.stream().map(DeptVO::from).toList();
        Map<Long, List<DeptVO>> byParent = vos.stream()
                .collect(Collectors.groupingBy(DeptVO::getParentId));
        for (DeptVO vo : vos) {
            vo.setChildren(new ArrayList<>(byParent.getOrDefault(vo.getId(), List.of())));
        }
        return new ArrayList<>(byParent.getOrDefault(0L, List.of()));
    }

    @Override
    public DeptVO getDetail(Long id) {
        return DeptVO.from(requireDept(id));
    }

    @Override
    public Long create(DeptForm form) {
        assertParentValid(form.getParentId(), null);
        SysDept dept = new SysDept();
        copyForm(form, dept);
        deptMapper.insert(dept);
        return dept.getId();
    }

    @Override
    public void update(DeptForm form) {
        requireDept(form.getId());
        assertParentValid(form.getParentId(), form.getId());
        SysDept dept = new SysDept();
        copyForm(form, dept);
        dept.setId(form.getId());
        deptMapper.updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDept(id);
        Long childCount = deptMapper.selectCount(Wrappers.lambdaQuery(SysDept.class)
                .eq(SysDept::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.system.dept-has-children");
        }
        Long userCount = userMapper.selectCount(Wrappers.lambdaQuery(SysUser.class)
                .eq(SysUser::getDeptId, id));
        if (userCount != null && userCount > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.system.dept-has-users");
        }
        deptMapper.deleteById(id);
    }

    /** 上级存在性 + 防环校验 */
    private void assertParentValid(Long parentId, Long selfId) {
        if (parentId == 0) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.system.parent-dept-self");
        }
        if (deptMapper.selectById(parentId) == null) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.system.parent-dept-not-exists");
        }
        if (selfId != null && collectDescendantIds(selfId).contains(parentId)) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.system.parent-dept-subordinate");
        }
    }

    private void copyForm(DeptForm form, SysDept dept) {
        dept.setParentId(form.getParentId());
        dept.setName(form.getName());
        dept.setSort(form.getSort() == null ? 0 : form.getSort());
        dept.setLeader(emptyToNull(form.getLeader()));
        dept.setPhone(emptyToNull(form.getPhone()));
        dept.setStatus(form.getStatus() == null ? 1 : form.getStatus());
    }

    private SysDept requireDept(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.system.dept-not-exists");
        }
        return dept;
    }

    /** BFS 收集自身及全部后代 ID */
    private Set<Long> collectDescendantIds(Long rootId) {
        List<SysDept> all = deptMapper.selectList(Wrappers.lambdaQuery(SysDept.class));
        Map<Long, List<Long>> childrenMap = all.stream()
                .collect(Collectors.groupingBy(SysDept::getParentId,
                        Collectors.mapping(SysDept::getId, Collectors.toList())));
        Set<Long> result = new HashSet<>();
        var queue = new ArrayDeque<>(childrenMap.getOrDefault(rootId, List.of()));
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(childrenMap.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    private String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

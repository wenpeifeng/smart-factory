package com.erp.system.dto.dept;

import com.erp.system.entity.SysDept;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点 VO。
 */
@Data
public class DeptVO {

    /** 部门 ID */
    private Long id;

    /** 上级 ID，0 为根 */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 显示顺序 */
    private Integer sort;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 子部门 */
    private List<DeptVO> children = new ArrayList<>();

    /**
     * 实体转 VO。
     *
     * @param dept 部门实体
     * @return VO（children 由树组装器填充）
     */
    public static DeptVO from(SysDept dept) {
        DeptVO vo = new DeptVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setName(dept.getName());
        vo.setSort(dept.getSort());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setStatus(dept.getStatus());
        vo.setCreateTime(dept.getCreateTime());
        return vo;
    }
}

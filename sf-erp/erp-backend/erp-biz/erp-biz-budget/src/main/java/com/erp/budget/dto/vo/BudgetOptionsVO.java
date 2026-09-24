package com.erp.budget.dto.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 预算编制基础选项 VO —— 部门树数据 + 可选预算科目（末级费用/成本类）。
 * <p>
 * 供前端矩阵编制（选部门/科目）与执行分析筛选下拉使用；
 * 数据来自 sys_dept / fin_subject 约定表只读查询。
 */
@Data
public class BudgetOptionsVO {

    /** 部门列表（含父子关系，前端组装 TreeSelect） */
    private List<DeptNodeVO> depts = new ArrayList<>();

    /** 末级费用/成本类科目列表 */
    private List<SubjectNodeVO> subjects = new ArrayList<>();

    /**
     * 部门节点。
     */
    @Data
    public static class DeptNodeVO {

        /** 部门 ID */
        private Long id;

        /** 父部门 ID（0=顶级） */
        private Long parentId;

        /** 部门名称 */
        private String name;

        /** 状态：1启用 0停用 */
        private Integer status;
    }

    /**
     * 科目节点（末级费用/成本类）。
     */
    @Data
    public static class SubjectNodeVO {

        /** 科目 ID */
        private Long id;

        /** 科目编码 */
        private String code;

        /** 科目名称 */
        private String name;
    }
}

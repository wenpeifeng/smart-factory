package com.erp.budget.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跨模块只读 Mapper —— 预算模块按约定表直查 system / finance 模块数据。
 * <p>
 * 并行开发约定（docs/开发规范.md）：跨模块【只按约定表结构只读查询，不 import 他人业务代码】。
 * 覆盖的约定表（表结构按各自迁移脚本对齐）：
 * <ul>
 *   <li>sys_dept（V11）—— 预算编制的部门维度；</li>
 *   <li>sys_user（V12）—— 编制人姓名、凭证制单人 → 部门归属（sys_user.dept_id）；</li>
 *   <li>fin_subject（V501）—— 预算科目（限末级费用/成本类：direction=DEBIT 且
 *       category IN (COST, PROFIT_LOSS) 且无子科目）；</li>
 *   <li>fin_voucher / fin_voucher_entry（V501）—— 凭证口径已用数：
 *       已过账(POSTED)凭证的费用成本科目借方发生额，按制单人部门归属。</li>
 * </ul>
 * 说明：fin_payment 无部门/科目列，付款类执行数无法只读归属，走
 * budget_usage_record 推式记账（BudgetControlService.record，接线约定见 docs/modules/budget.md）。
 * 本 Mapper 只读，禁止出现 INSERT/UPDATE/DELETE。
 */
public interface CrossModuleMapper {

    /**
     * 部门引用（编制维度/展示）。
     */
    @Data
    class DeptRef {
        private Long id;
        /** 父部门 ID（0=顶级） */
        private Long parentId;
        /** 部门名称 */
        private String name;
        /** 状态：1启用 0停用 */
        private Integer status;
    }

    /**
     * 科目引用（末级费用/成本类）。
     */
    @Data
    class SubjectRef {
        private Long id;
        /** 科目编码 */
        private String code;
        /** 科目名称 */
        private String name;
    }

    /**
     * 部门×科目×月份 的凭证借方汇总行（执行分析/曲线用）。
     */
    @Data
    class VoucherUsedRow {
        /** 制单人所属部门 ID */
        private Long deptId;
        /** 科目 ID */
        private Long subjectId;
        /** 月份（1-12） */
        private Integer month;
        /** 借方发生额合计 */
        private BigDecimal amount;
    }

    /**
     * 全部在用部门（含停用，保留历史预算行展示口径）。
     */
    @Select("""
            SELECT id, parent_id AS parentId, name, status
            FROM sys_dept WHERE deleted = 0
            ORDER BY parent_id, id
            """)
    List<DeptRef> listDepts();

    /**
     * 单个部门引用（校验用）。
     *
     * @return 不存在返回 null
     */
    @Select("SELECT id, parent_id AS parentId, name, status "
            + "FROM sys_dept WHERE id = #{id} AND deleted = 0")
    DeptRef findDept(@Param("id") Long id);

    /**
     * 全部【末级费用/成本类】科目（direction=DEBIT 且 category 为 COST/PROFIT_LOSS
     * 且无在用子科目），预算编制只允许选这些科目。
     */
    @Select("""
            SELECT s.id, s.code, s.name
            FROM fin_subject s
            WHERE s.deleted = 0 AND s.status = 1
              AND s.direction = 'DEBIT'
              AND s.category IN ('COST', 'PROFIT_LOSS')
              AND NOT EXISTS (SELECT 1 FROM fin_subject c
                              WHERE c.parent_id = s.id AND c.deleted = 0)
            ORDER BY s.code
            """)
    List<SubjectRef> listExpenseSubjects();

    /**
     * 单个科目引用（校验用）。
     *
     * @return 不存在返回 null
     */
    @Select("SELECT id, code, name FROM fin_subject WHERE id = #{id} AND deleted = 0")
    SubjectRef findSubject(@Param("id") Long id);

    /**
     * 科目在用子科目数（0=末级）。
     */
    @Select("SELECT COUNT(*) FROM fin_subject c WHERE c.parent_id = #{id} AND c.deleted = 0")
    int countChildSubjects(@Param("id") Long id);

    /**
     * 科目是否费用/成本类借方科目（预算可编科目）。
     */
    @Select("""
            SELECT COUNT(*) FROM fin_subject
            WHERE id = #{id} AND deleted = 0 AND status = 1
              AND direction = 'DEBIT' AND category IN ('COST', 'PROFIT_LOSS')
            """)
    int countBudgetableSubject(@Param("id") Long id);

    /**
     * 凭证口径已用数：已过账凭证中该部门(制单人归属)该科目该月的借方合计。
     * <p>
     * 部门归属约定：fin_voucher 无部门列，取制单人(sys_user.dept_id)归属；
     * 制单人无部门的凭证不计入任何部门。
     *
     * @return 无发生返回 null（调用方按 0 处理）
     */
    @Select("""
            SELECT COALESCE(SUM(e.debit_amount), 0)
            FROM fin_voucher_entry e
            JOIN fin_voucher v ON v.id = e.voucher_id AND v.deleted = 0 AND v.status = 'POSTED'
            JOIN sys_user u ON u.id = v.create_by AND u.deleted = 0 AND u.dept_id = #{deptId}
            WHERE e.deleted = 0 AND e.subject_id = #{subjectId}
              AND YEAR(v.voucher_date) = #{year} AND MONTH(v.voucher_date) = #{month}
            """)
    BigDecimal sumVoucherDebit(@Param("deptId") Long deptId, @Param("subjectId") Long subjectId,
                               @Param("year") Integer year, @Param("month") Integer month);

    /**
     * 凭证口径已用数（分组）：某年度（可筛部门）按 部门×科目×月份 汇总的借方发生额，
     * 供执行分析/月度曲线一次性取数。
     *
     * @param deptId 可空 = 全部部门
     */
    @Select("""
            <script>
            SELECT u.dept_id AS deptId, e.subject_id AS subjectId,
                   MONTH(v.voucher_date) AS month, SUM(e.debit_amount) AS amount
            FROM fin_voucher_entry e
            JOIN fin_voucher v ON v.id = e.voucher_id AND v.deleted = 0 AND v.status = 'POSTED'
            JOIN sys_user u ON u.id = v.create_by AND u.deleted = 0 AND u.dept_id IS NOT NULL
            WHERE e.deleted = 0 AND YEAR(v.voucher_date) = #{year}
            <if test="deptId != null">AND u.dept_id = #{deptId}</if>
            GROUP BY u.dept_id, e.subject_id, MONTH(v.voucher_date)
            </script>
            """)
    List<VoucherUsedRow> sumVoucherUsedGrouped(@Param("year") Integer year,
                                               @Param("deptId") Long deptId);

    /**
     * 用户昵称（编制人展示）。
     */
    @Select("SELECT nickname FROM sys_user WHERE id = #{id} AND deleted = 0")
    String selectUserNickname(@Param("id") Long id);
}

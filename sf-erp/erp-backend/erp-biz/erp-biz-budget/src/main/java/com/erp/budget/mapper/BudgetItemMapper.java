package com.erp.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.budget.entity.BudgetItem;
import org.apache.ibatis.annotations.Param;

/**
 * 预算明细 Mapper。
 * <p>
 * 单元格保存走 {@link #upsertCell}（ON DUPLICATE KEY，顺带复活逻辑删行），
 * 避免 uk(plan,dept,subject,month) 与逻辑删行冲突。
 */
public interface BudgetItemMapper extends BaseMapper<BudgetItem> {

    /**
     * 单元格 upsert：不存在则插入，存在（含逻辑删行）则更新金额并复活。
     *
     * @param cell 明细单元格（planId/deptId/subjectId/month/amount 必填，
     *             createBy/updateBy 由调用方手动置入——自定义 SQL 不走 MP 审计填充）
     * @return 影响行数（1=插入，2=更新）
     */
    int upsertCell(@Param("cell") BudgetItem cell);
}

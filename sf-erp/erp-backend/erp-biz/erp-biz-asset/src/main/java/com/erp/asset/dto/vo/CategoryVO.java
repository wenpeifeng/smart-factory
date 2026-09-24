package com.erp.asset.dto.vo;

import com.erp.asset.entity.AstCategory;
import com.erp.asset.entity.FinSubject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产类别 VO（含科目编码/名称回显）。
 *
 * @param id                              类别 ID
 * @param code                            类别编码
 * @param name                            类别名称
 * @param depreciationMethod              折旧方法（AVG）
 * @param expectedLifeMonths              预计使用年限（月）
 * @param residualRate                    残值率（%）
 * @param assetSubjectId                  资产科目 ID
 * @param assetSubjectLabel               资产科目（编码 名称）
 * @param accumulatedDepreciationSubjectId 累计折旧科目 ID
 * @param accumulatedSubjectLabel         累计折旧科目（编码 名称）
 * @param depreciationExpenseSubjectId    折旧费用科目 ID
 * @param expenseSubjectLabel             折旧费用科目（编码 名称）
 * @param remark                          备注
 * @param createTime                      创建时间
 */
public record CategoryVO(
        Long id,
        String code,
        String name,
        String depreciationMethod,
        Integer expectedLifeMonths,
        BigDecimal residualRate,
        Long assetSubjectId,
        String assetSubjectLabel,
        Long accumulatedDepreciationSubjectId,
        String accumulatedSubjectLabel,
        Long depreciationExpenseSubjectId,
        String expenseSubjectLabel,
        String remark,
        LocalDateTime createTime) {

    /** 实体 → VO（科目实体允许为 null：科目被删时降级只显示 ID） */
    public static CategoryVO from(AstCategory c, FinSubject assetSubject,
                                  FinSubject accumulatedSubject, FinSubject expenseSubject) {
        return new CategoryVO(c.getId(), c.getCode(), c.getName(), c.getDepreciationMethod(),
                c.getExpectedLifeMonths(), c.getResidualRate(),
                c.getAssetSubjectId(), label(assetSubject),
                c.getAccumulatedDepreciationSubjectId(), label(accumulatedSubject),
                c.getDepreciationExpenseSubjectId(), label(expenseSubject),
                c.getRemark(), c.getCreateTime());
    }

    /** 科目 → "编码 名称" 展示串 */
    static String label(FinSubject subject) {
        return subject == null ? null : subject.getCode() + " " + subject.getName();
    }
}

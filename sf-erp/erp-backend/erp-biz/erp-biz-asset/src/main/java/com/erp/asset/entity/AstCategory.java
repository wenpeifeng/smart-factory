package com.erp.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 资产类别实体（表 ast_category）。
 * <p>
 * 承载折旧政策（折旧方法/预计年限/残值率）与凭证科目映射
 * （资产科目 / 累计折旧科目 / 折旧费用科目，逻辑关联 fin_subject）。
 * 折旧方法当前仅实现 AVG（平均年限法）；WORKLOAD（工作量法）预留给后续迭代。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_category")
public class AstCategory extends BaseEntity {

    /** 类别编码，全局唯一（如 DZ 电子设备 / YS 运输设备 / JQ 机器设备） */
    private String code;

    /** 类别名称 */
    private String name;

    /** 折旧方法：AVG-平均年限法 WORKLOAD-工作量法（暂未实现） */
    private String depreciationMethod;

    /** 预计使用年限（月），平均年限法的分母 */
    private Integer expectedLifeMonths;

    /** 残值率（%），如 5.00 表示 5%；新增卡片未显式填残值时按 原值×残值率 计算默认残值 */
    private BigDecimal residualRate;

    /** 资产科目 ID（fin_subject，入账凭证借方） */
    private Long assetSubjectId;

    /** 累计折旧科目 ID（fin_subject，折旧凭证贷方 / 处置转出借方） */
    private Long accumulatedDepreciationSubjectId;

    /** 折旧费用科目 ID（fin_subject，折旧凭证借方） */
    private Long depreciationExpenseSubjectId;

    /** 备注 */
    private String remark;
}

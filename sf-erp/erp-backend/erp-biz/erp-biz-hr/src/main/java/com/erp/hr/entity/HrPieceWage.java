package com.erp.hr.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 计件工资期间 —— 一月一期间（period=yyyy-MM 唯一）。
 * 状态机：OPEN开账（首次归集自动建账）→ CLOSED已结账（拒绝再归集），单向。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_piece_wage")
public class HrPieceWage extends BaseEntity implements Serializable {

    /** 期间（yyyy-MM） */
    private String period;

    /** 状态：OPEN开账 CLOSED已结账 */
    private String status;

    /** 备注 */
    private String remark;
}

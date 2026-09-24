package com.erp.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产卡片实体（表 ast_asset）。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>净值 net_value 为数据库生成列（原值−累计折旧），只读，INSERT/UPDATE 永不参与；</li>
 *   <li>状态机：IN_USE 在用 ⇄ IDLE 闲置（可互转）；IN_USE/IDLE → DISPOSED 处置（单向终态，
 *       只能通过 POST /asset/assets/{id}/dispose 流转，处置后停提折旧、禁改禁删）；</li>
 *   <li>累计折旧/已提月数只允许折旧计提事务内的条件 UPDATE 变更，防止并发多提。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ast_asset")
public class AstAsset extends BaseEntity {

    /** 资产状态：在用 */
    public static final String STATUS_IN_USE = "IN_USE";
    /** 资产状态：闲置 */
    public static final String STATUS_IDLE = "IDLE";
    /** 资产状态：处置（终态） */
    public static final String STATUS_DISPOSED = "DISPOSED";

    /** 资产编码（ZC + 4 位流水，Redis 自增生成，全局唯一） */
    private String assetCode;

    /** 资产名称 */
    private String name;

    /** 类别 ID（ast_category.id） */
    private Long categoryId;

    /** 规格型号 */
    private String spec;

    /** 供应商 ID（base_supplier.id，可空） */
    private Long supplierId;

    /** 存放地点 */
    private String location;

    /** 使用部门 ID（sys_dept.id，可空；折旧费用按部门汇总的关键维度） */
    private Long deptId;

    /** 责任人 */
    private String keeper;

    /** 原值 */
    private BigDecimal originalValue;

    /** 残值 */
    private BigDecimal residualValue;

    /** 预计使用年限（月） */
    private Integer expectedLifeMonths;

    /** 已提折旧月数（折旧计提事务内 +1） */
    private Integer depreciatedMonths;

    /** 累计折旧（折旧计提事务内累加） */
    private BigDecimal accumulatedDepreciation;

    /** 净值 = 原值 − 累计折旧（数据库生成列，只读，禁止 INSERT/UPDATE） */
    @TableField(value = "net_value", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private BigDecimal netValue;

    /** 入账日期（入账凭证的凭证日期） */
    private LocalDate accountDate;

    /** 状态：IN_USE-在用 IDLE-闲置 DISPOSED-处置 */
    private String status;

    /** 入账凭证 ID（新增卡片时自动生成） */
    private Long entryVoucherId;

    /** 处置方式：SELL-出售 SCRAP-报废 DONATE-捐赠 */
    private String disposeMethod;

    /** 清理收入（处置时收取） */
    private BigDecimal disposeIncome;

    /** 处置时间 */
    private LocalDateTime disposeTime;

    /** 处置凭证 ID（处置时自动生成） */
    private Long disposeVoucherId;

    /** 备注 */
    private String remark;
}

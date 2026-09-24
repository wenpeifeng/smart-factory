package com.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账凭证主实体（表 fin_voucher）。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>保存前必须借贷平衡（total_debit 与 total_credit 用 BigDecimal compareTo 比较）；</li>
 *   <li>状态机单向：DRAFT 草稿 → POSTED 已过账；过账后禁止修改与删除。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher")
public class FinVoucher extends BaseEntity {

    /** 所属账套 ID（fin_book.id，多账套隔离键：Service 层统一注入查询条件与写入值）*/
    private Long bookId;

    /** 凭证号（PZ + yyyyMMdd + 4 位流水，Redis 自增生成） */
    private String voucherNo;

    /** 凭证日期（记账所属期间） */
    private LocalDate voucherDate;

    /** 凭证字：记 / 收 / 付 / 转 */
    private String word;

    /** 附件张数 */
    private Integer attachmentCount;

    /** 借方金额合计 */
    private BigDecimal totalDebit;

    /** 贷方金额合计 */
    private BigDecimal totalCredit;

    /** 状态：DRAFT-草稿 POSTED-已过账 */
    private String status;

    /** 审核人 */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    /** 业务来源类型（V521 模板引擎：PUR_INSTOCK/SAL_OUTSTOCK/... 八类；NULL=手工凭证） */
    private String sourceType;

    /** 业务来源单据 ID（与 sourceType 联合定位来源单，uk_voucher_source 幂等键成分；NULL=手工凭证） */
    private Long sourceId;
}

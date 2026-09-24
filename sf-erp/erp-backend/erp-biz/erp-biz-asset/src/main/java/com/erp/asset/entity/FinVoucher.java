package com.erp.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账凭证主表本地映射（表 fin_voucher，erp-finance 为表 owner）。
 * <p>
 * 并行协作约定：固定资产模块自动凭证（入账/折旧/处置）直接按 finance.md 第 6 节的
 * 约定表结构写入 fin_voucher / fin_voucher_entry，<b>不 import erp-finance 业务代码</b>；
 * 本类是字段级镜像（与 FinVoucherServiceImpl 的写入规则保持一致：
 * 借贷平衡、行号连续、凭证号与 finance 共用同一 Redis PZ 序列）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher")
public class FinVoucher extends BaseEntity {

    /** 凭证号（PZ + yyyyMMdd + 4 位流水，Redis 自增生成，与 finance 模块共用序列） */
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

    /** 状态：DRAFT-草稿 POSTED-已过账；自动凭证生成即 POSTED（禁改禁删，与手工凭证状态机一致） */
    private String status;

    /** 审核人 */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;
}

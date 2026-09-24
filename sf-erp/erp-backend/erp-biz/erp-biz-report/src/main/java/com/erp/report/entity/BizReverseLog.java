package com.erp.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用红冲台账（biz_reverse_log，V541）：一行记录"原单 → 红冲单"的配对关系。
 * <p>
 * 原单标记：出入库/退货单额外回写 status=20（REVERSED）；收付款单无 status 列，
 * 以本表存在 source 记录为准（联查/列表均按本表判断）。
 */
@Data
@TableName("biz_reverse_log")
public class BizReverseLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原单类型（DocTypes 常量，如 PUR_INSTOCK） */
    private String sourceType;

    /** 原单 ID */
    private Long sourceId;

    /** 原单号 */
    private String sourceNo;

    /** 红冲单类型（与原单相同） */
    private String reverseType;

    /** 红冲单 ID */
    private Long reverseId;

    /** 红冲单号（原单号 + R） */
    private String reverseNo;

    /** 红冲原因 */
    private String reason;

    /** 操作人 */
    private Long reverseBy;

    /** 操作时间 */
    private LocalDateTime reverseTime;
}

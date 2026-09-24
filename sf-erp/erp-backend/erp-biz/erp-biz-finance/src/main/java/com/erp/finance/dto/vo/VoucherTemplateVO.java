package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinVoucherTemplate;

import java.time.LocalDateTime;

/**
 * 凭证模板 VO（V521）。
 *
 * @param id         模板 ID
 * @param code       模板编码
 * @param name       模板名称
 * @param bizType    单据类型（八类）
 * @param entryRules 分录规则 JSON 数组字符串
 * @param enabled    启用：1/0
 * @param priority   优先级（预留）
 * @param remark     备注
 * @param updateTime 最近更新时间
 */
public record VoucherTemplateVO(Long id, String code, String name, String bizType,
                                String entryRules, Integer enabled, Integer priority,
                                String remark, LocalDateTime updateTime) {

    /** 实体转 VO */
    public static VoucherTemplateVO from(FinVoucherTemplate t) {
        return new VoucherTemplateVO(t.getId(), t.getCode(), t.getName(), t.getBizType(),
                t.getEntryRules(), t.getEnabled(), t.getPriority(), t.getRemark(), t.getUpdateTime());
    }
}

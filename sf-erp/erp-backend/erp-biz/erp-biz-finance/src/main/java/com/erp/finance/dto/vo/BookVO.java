package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinBook;

import java.time.LocalDateTime;

/**
 * 账套 VO。
 *
 * @param id           账套 ID（请求头 X-Book-Id 使用该值）
 * @param code         账套编码（BS+流水）
 * @param name         账套名称
 * @param periodPolicy 会计期间政策
 * @param currency     本位币
 * @param isDefault    是否默认账套（默认账套不可删）
 * @param status       状态：1-启用 0-停用
 * @param remark       备注
 * @param createTime   创建时间
 */
public record BookVO(Long id, String code, String name, String periodPolicy, String currency,
                     Integer isDefault, Integer status, String remark, LocalDateTime createTime) {

    /** 实体转 VO */
    public static BookVO from(FinBook b) {
        return new BookVO(b.getId(), b.getCode(), b.getName(), b.getPeriodPolicy(), b.getCurrency(),
                b.getIsDefault(), b.getStatus(), b.getRemark(), b.getCreateTime());
    }
}

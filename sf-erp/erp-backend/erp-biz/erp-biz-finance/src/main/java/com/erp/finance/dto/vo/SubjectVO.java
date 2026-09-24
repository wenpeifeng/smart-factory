package com.erp.finance.dto.vo;

import com.erp.finance.entity.FinSubject;

/**
 * 会计科目 VO。
 *
 * @param id       科目 ID
 * @param code     科目编码
 * @param name     科目名称
 * @param parentId 上级科目 ID（0 = 一级）
 * @param category 科目类别
 * @param direction 余额方向
 * @param status   状态：1-启用 0-停用
 * @param remark   备注
 * @param leaf     是否末级科目（凭证分录只允许末级科目）
 */
public record SubjectVO(Long id, String code, String name, Long parentId, String category,
                        String direction, Integer status, String remark, boolean leaf) {

    /** 实体转 VO（leaf 由调用方依据子科目数判定后传入） */
    public static SubjectVO from(FinSubject s, boolean leaf) {
        return new SubjectVO(s.getId(), s.getCode(), s.getName(),
                s.getParentId(), s.getCategory(), s.getDirection(),
                s.getStatus(), s.getRemark(), leaf);
    }
}

package com.mes.system.dto.dict;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 公开字典项 VO（GET /dict/{type}）。
 * <p>
 * 面向所有业务模块的下拉数据：label 展示 / value 存储。
 */
@Data
@AllArgsConstructor
public class DictItemVO {

    /** 选项标签（展示） */
    private String label;

    /** 选项值（存储） */
    private String value;

    /** 显示顺序 */
    private Integer sort;
}

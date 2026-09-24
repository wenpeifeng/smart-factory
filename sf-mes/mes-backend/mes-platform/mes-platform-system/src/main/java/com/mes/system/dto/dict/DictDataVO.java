package com.mes.system.dto.dict;

import com.mes.system.entity.SysDictData;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典数据响应 VO。
 */
@Data
public class DictDataVO {

    /** 字典项 ID */
    private Long id;

    /** 所属字典类型编码 */
    private String dictType;

    /** 选项标签（展示） */
    private String label;

    /** 选项值（存储） */
    private String value;

    /** 显示顺序 */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param entity 字典数据实体
     * @return VO
     */
    public static DictDataVO from(SysDictData entity) {
        DictDataVO vo = new DictDataVO();
        vo.setId(entity.getId());
        vo.setDictType(entity.getDictType());
        vo.setLabel(entity.getLabel());
        vo.setValue(entity.getValue());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}

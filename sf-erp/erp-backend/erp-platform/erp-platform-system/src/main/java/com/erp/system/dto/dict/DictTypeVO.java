package com.erp.system.dto.dict;

import com.erp.system.entity.SysDictType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型响应 VO。
 */
@Data
public class DictTypeVO {

    /** 类型 ID */
    private Long id;

    /** 字典名称 */
    private String name;

    /** 类型编码 */
    private String type;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param entity 字典类型实体
     * @return VO
     */
    public static DictTypeVO from(SysDictType entity) {
        DictTypeVO vo = new DictTypeVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}

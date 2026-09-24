package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.BaseMaterialCategory;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料分类树节点 VO。
 * <p>树由 Service 一次取全表后在内存组装（分类量级小）。
 */
@Data
public class BaseMaterialCategoryVO {

    /** 主键 */
    private Long id;

    /** 父分类 ID，0=根节点 */
    private Long parentId;

    /** 分类编码 */
    private String code;

    /** 分类名称 */
    private String name;

    /** 排序号 */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 子分类 */
    private List<BaseMaterialCategoryVO> children = new ArrayList<>();

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换（children 由树组装逻辑填充）。
     *
     * @param entity 分类实体
     * @return VO
     */
    public static BaseMaterialCategoryVO from(BaseMaterialCategory entity) {
        BaseMaterialCategoryVO vo = new BaseMaterialCategoryVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}

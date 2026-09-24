package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.BaseSupplier;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商响应 VO。
 */
@Data
public class BaseSupplierVO {

    /** 主键 */
    private Long id;

    /** 供应商编码 */
    private String code;

    /** 供应商名称 */
    private String name;

    /** 联系人 */
    private String contact;

    /** 手机号 */
    private String phone;

    /** 地址 */
    private String address;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 结算方式 ID */
    private Long settleTypeId;

    /** 结算方式名称（冗余） */
    private String settleTypeName;

    /** 期初应付 */
    private BigDecimal initPayable;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换（结算方式名称由 Service 填充）。
     *
     * @param entity 供应商实体
     * @return VO
     */
    public static BaseSupplierVO from(BaseSupplier entity) {
        BaseSupplierVO vo = new BaseSupplierVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setContact(entity.getContact());
        vo.setPhone(entity.getPhone());
        vo.setAddress(entity.getAddress());
        vo.setCreditCode(entity.getCreditCode());
        vo.setSettleTypeId(entity.getSettleTypeId());
        vo.setInitPayable(entity.getInitPayable());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}

package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.BaseCustomer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户响应 VO。
 */
@Data
public class BaseCustomerVO {

    /** 主键 */
    private Long id;

    /** 客户编码 */
    private String code;

    /** 客户名称 */
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

    /** 信用额度 */
    private BigDecimal creditLimit;

    /** 期初应收 */
    private BigDecimal initReceivable;

    /** 客户等级：A/B/C（等级价策略匹配，默认 B） */
    private String level;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换（结算方式名称由 Service 填充）。
     *
     * @param entity 客户实体
     * @return VO
     */
    public static BaseCustomerVO from(BaseCustomer entity) {
        BaseCustomerVO vo = new BaseCustomerVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setContact(entity.getContact());
        vo.setPhone(entity.getPhone());
        vo.setAddress(entity.getAddress());
        vo.setCreditCode(entity.getCreditCode());
        vo.setSettleTypeId(entity.getSettleTypeId());
        vo.setCreditLimit(entity.getCreditLimit());
        vo.setInitReceivable(entity.getInitReceivable());
        vo.setLevel(entity.getLevel() == null || entity.getLevel().isBlank() ? "B" : entity.getLevel());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}

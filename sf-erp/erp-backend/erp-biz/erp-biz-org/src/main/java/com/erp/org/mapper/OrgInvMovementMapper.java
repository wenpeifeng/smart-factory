package com.erp.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.org.entity.OrgInvMovement;

/**
 * 库存流水写入 Mapper（inv_movement，inventory 模块约定表）。
 * <p>
 * 组织间调拨审核时按约定写 ORG_OUT / ORG_IN 两行（qty 正入负出、
 * biz_id=调拨单 id、biz_no/remark 记 IT 单号），与库存变更同事务。
 * 审计列（create_by 等）由 MP 审计填充器自动写入。
 */
public interface OrgInvMovementMapper extends BaseMapper<OrgInvMovement> {
}

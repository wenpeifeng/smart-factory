package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.system.dto.tenant.TenantForm;
import com.mes.system.dto.tenant.TenantQuery;
import com.mes.system.dto.tenant.TenantVO;
import com.mes.system.entity.SysTenant;
import com.mes.system.mapper.SysTenantMapper;
import com.mes.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 租户管理服务实现。
 * <p>
 * 默认租户（id=1000，V1011 种子）承载全部存量数据，禁止停用/改码/删除，
 * 其余租户可正常维护；编码唯一由 uk_tenant_code 兜底。
 */
@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl implements SysTenantService {

    /** 默认租户 ID（存量数据归属） */
    private static final long DEFAULT_TENANT_ID = 1000L;

    private final SysTenantMapper tenantMapper;

    @Override
    public PageResult<TenantVO> page(TenantQuery query) {
        var wrapper = Wrappers.lambdaQuery(SysTenant.class)
                .and(notBlank(query.getKeyword()), w -> w
                        .like(SysTenant::getCode, query.getKeyword().trim())
                        .or().like(SysTenant::getName, query.getKeyword().trim()))
                .eq(query.getStatus() != null, SysTenant::getStatus, query.getStatus())
                .orderByDesc(SysTenant::getId);
        return PageUtils.of(tenantMapper, wrapper, query, SysTenant.class, TenantVO::from);
    }

    @Override
    public List<TenantVO> listEnabled() {
        return tenantMapper.selectList(Wrappers.lambdaQuery(SysTenant.class)
                        .eq(SysTenant::getStatus, 1))
                .stream().map(TenantVO::from).toList();
    }

    @Override
    public TenantVO getDetail(Long id) {
        return TenantVO.from(requireTenant(id));
    }

    @Override
    public Long create(TenantForm form) {
        assertCodeUnused(form.getCode(), null);
        SysTenant tenant = new SysTenant();
        tenant.setCode(form.getCode());
        tenant.setName(form.getName());
        tenant.setContact(form.getContact());
        tenant.setPhone(form.getPhone());
        tenant.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        tenant.setExpireTime(form.getExpireTime());
        tenant.setRemark(form.getRemark());
        try {
            tenantMapper.insert(tenant);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("租户编码[%s]已存在", form.getCode());
        }
        return tenant.getId();
    }

    @Override
    public void update(TenantForm form) {
        SysTenant existing = requireTenant(form.getId());
        if (existing.getId() == DEFAULT_TENANT_ID && !existing.getCode().equals(form.getCode())) {
            throw new BusinessException("默认租户编码不允许修改（存量数据隔离锚点）");
        }
        assertCodeUnused(form.getCode(), form.getId());
        SysTenant tenant = new SysTenant();
        tenant.setId(form.getId());
        tenant.setCode(form.getCode());
        tenant.setName(form.getName());
        tenant.setContact(form.getContact());
        tenant.setPhone(form.getPhone());
        tenant.setStatus(form.getStatus());
        tenant.setExpireTime(form.getExpireTime());
        tenant.setRemark(form.getRemark());
        try {
            tenantMapper.updateById(tenant);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("租户编码[%s]已存在", form.getCode());
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (id == DEFAULT_TENANT_ID && status != null && status == 0) {
            throw new BusinessException("默认租户不允许停用");
        }
        requireTenant(id);
        SysTenant tenant = new SysTenant();
        tenant.setId(id);
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
    }

    /** 校验编码未被占用（排除自身） */
    private void assertCodeUnused(String code, Long excludeId) {
        Long count = tenantMapper.selectCount(Wrappers.lambdaQuery(SysTenant.class)
                .eq(SysTenant::getCode, code)
                .ne(excludeId != null, SysTenant::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("租户编码[%s]已存在", code);
        }
    }

    /** 按主键取租户，不存在抛业务异常 */
    private SysTenant requireTenant(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "租户不存在或已被删除");
        }
        return tenant;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

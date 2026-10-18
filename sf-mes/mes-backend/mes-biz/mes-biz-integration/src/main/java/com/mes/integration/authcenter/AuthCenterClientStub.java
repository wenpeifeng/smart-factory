package com.mes.integration.authcenter;

import com.mes.integration.authcenter.dto.AuthCenterUserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * auth-center 客户端契约桩（mode=stub 默认）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mes.integration.auth-center", name = "mode", havingValue = "stub", matchIfMissing = true)
public class AuthCenterClientStub implements AuthCenterClient {

    @Override
    public List<AuthCenterUserDTO> fetchUsers(String since) {
        log.info("[AUTH-STUB] 拉取用户清单: since={}", since);
        return List.of();
    }

    @Override
    public void disableUser(String employeeNo) {
        log.info("[AUTH-STUB] 应用级禁用用户: {}", employeeNo);
    }

    @Override
    public void enableUser(String employeeNo) {
        log.info("[AUTH-STUB] 应用级启用用户: {}", employeeNo);
    }

    @Override
    public void updateProfile(String employeeNo, ProfileForm form) {
        log.info("[AUTH-STUB] 更新用户档案: {}", employeeNo);
    }

    @Override
    public void resetPassword(String employeeNo, String password) {
        log.info("[AUTH-STUB] 重置用户密码: {}", employeeNo);
    }
}

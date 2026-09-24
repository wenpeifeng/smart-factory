package com.erp.notify.dto;

import com.erp.notify.entity.NotifyUserBinding;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户绑定 VO。
 */
@Data
public class BindingVO {

    /** 绑定 ID */
    private Long id;

    /** ERP 用户 ID */
    private Long userId;

    /** 用户昵称（sys_user.nickname，展示用） */
    private String userName;

    /** 通道：dingtalk/wecom */
    private String channel;

    /** 外部标识：钉钉=手机号 / 企微=成员 userid */
    private String externalId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 实体转 VO（昵称由服务层批量补充）。
     *
     * @param binding 绑定实体
     * @return VO
     */
    public static BindingVO from(NotifyUserBinding binding) {
        BindingVO vo = new BindingVO();
        vo.setId(binding.getId());
        vo.setUserId(binding.getUserId());
        vo.setChannel(binding.getChannel());
        vo.setExternalId(binding.getExternalId());
        vo.setCreateTime(binding.getCreateTime());
        return vo;
    }
}

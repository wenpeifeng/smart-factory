package com.mes.notify.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 通知模块跨模块只读/兜底 Mapper。
 * <p>
 * 与工作流 WfRefMapper 同一约定：跨模块只按约定表结构读写，不 import 他人业务代码。
 * 本模块只涉及两张外部表：
 * <ul>
 *   <li>sys_user：查昵称（降级站内信文案用）；</li>
 *   <li>wf_message：外部通道发送失败/未绑定时的站内信降级落库（workflow 侧正常消息
 *       仍由其自己的 messageService 写入，这里仅补"通道降级"记录）。</li>
 * </ul>
 */
public interface NotifyRefMapper {

    /**
     * 查用户昵称。
     *
     * @param userId 用户 ID
     * @return 昵称；不存在返回 null
     */
    @Select("SELECT nickname FROM sys_user WHERE id = #{userId} AND deleted = 0")
    String selectUserNickname(@Param("userId") Long userId);

    /**
     * 站内信降级：外部通道发送失败时写 wf_message（接收人仍可在消息中心收到提醒）。
     *
     * @param receiverId 接收人用户 ID
     * @param msgType    TODO/APPROVE/REJECT/WARN
     * @param title      标题
     * @param content    内容
     * @param bizPath    前端跳转路径
     * @param tenantId   租户 ID（事件线程可能无租户上下文，显式带入）
     */
    @Insert("""
            INSERT INTO wf_message (receiver_id, msg_type, title, content, biz_path, read_flag,
                                    tenant_id, org_id, create_by, create_time, deleted)
            VALUES (#{receiverId}, #{msgType}, #{title}, #{content}, #{bizPath}, 0,
                    #{tenantId}, 0, 0, NOW(), 0)
            """)
    int insertFallbackMessage(@Param("receiverId") Long receiverId,
                              @Param("msgType") String msgType,
                              @Param("title") String title,
                              @Param("content") String content,
                              @Param("bizPath") String bizPath,
                              @Param("tenantId") Long tenantId);
}

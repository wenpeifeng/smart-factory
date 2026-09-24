package com.erp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.workflow.dto.vo.WfMessageVO;
import com.erp.workflow.entity.WfMessage;
import org.apache.ibatis.annotations.Param;

/**
 * 消息中心 Mapper。
 */
public interface WfMessageMapper extends BaseMapper<WfMessage> {

    /**
     * 分页查询我的消息（带类型/未读过滤）。
     *
     * @param page       分页参数（MP 自动注入）
     * @param receiverId 接收人（当前登录人）
     * @param msgType    消息类型（可空）
     * @param unreadOnly 仅未读（可空）
     * @return 消息 VO 分页
     */
    IPage<WfMessageVO> selectMessagePage(Page<WfMessageVO> page,
                                         @Param("receiverId") Long receiverId,
                                         @Param("msgType") String msgType,
                                         @Param("unreadOnly") Boolean unreadOnly);
}

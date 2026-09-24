package com.mes.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.workflow.dto.vo.WfInstanceVO;
import com.mes.workflow.entity.WfInstance;
import org.apache.ibatis.annotations.Param;

/**
 * 流程实例 Mapper。
 */
public interface WfInstanceMapper extends BaseMapper<WfInstance> {

    /**
     * 分页查询流程实例（带流程名/发起人姓名/当前待办节点）。
     *
     * @param page        分页参数（MP 自动注入）
     * @param startUserId 发起人（为 null 查全部，管理视角）
     * @param keyword     单号模糊
     * @param bizType     单据类型
     * @param status      实例状态
     * @return 实例 VO 分页
     */
    IPage<WfInstanceVO> selectInstancePage(Page<WfInstanceVO> page,
                                           @Param("startUserId") Long startUserId,
                                           @Param("keyword") String keyword,
                                           @Param("bizType") String bizType,
                                           @Param("status") String status);
}

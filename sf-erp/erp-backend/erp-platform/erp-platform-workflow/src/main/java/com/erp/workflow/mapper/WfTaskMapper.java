package com.erp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.workflow.dto.vo.WfTaskVO;
import com.erp.workflow.entity.WfTask;
import org.apache.ibatis.annotations.Param;

/**
 * 审批任务 Mapper。
 */
public interface WfTaskMapper extends BaseMapper<WfTask> {

    /**
     * 分页查询我的任务（待办/已办同一 SQL，按 done 切换状态过滤）。
     *
     * @param page       分页参数（MP 自动注入）
     * @param approverId 审批人（当前登录人）
     * @param done       true=已办（非 PENDING），false=待办（PENDING）
     * @param keyword    单号模糊
     * @param bizType    单据类型
     * @return 任务 VO 分页
     */
    IPage<WfTaskVO> selectTaskPage(Page<WfTaskVO> page,
                                   @Param("approverId") Long approverId,
                                   @Param("done") boolean done,
                                   @Param("keyword") String keyword,
                                   @Param("bizType") String bizType);
}

package com.mes.plan.service;

import com.mes.common.result.PageResult;
import com.mes.plan.dto.vo.KitCheckDetailVO;
import com.mes.plan.dto.vo.KitCheckVO;

/**
 * 齐套分析服务：按工单用料需求 vs 线边仓可用量逐料比对，生成快照。
 */
public interface KitCheckService {

    /**
     * 对工单执行一次齐套分析（要求工单已下达）。
     *
     * @param workorderId 工单 id
     * @return 分析单 id
     */
    Long check(Long workorderId);

    /**
     * 分页查询齐套分析记录。
     *
     * @param pageNum    页码
     * @param pageSize   页大小
     * @param keyword    工单号/分析单号模糊
     * @param result     结果过滤（FULL/SHORT/PART）
     * @return 分页结果
     */
    PageResult<KitCheckVO> page(long pageNum, long pageSize, String keyword, String result);

    /**
     * 查询工单最近一次齐套分析详情。
     *
     * @param workorderId 工单 id
     * @return 详情（含逐料行），无记录返回 null
     */
    KitCheckDetailVO latestByWorkorder(Long workorderId);
}

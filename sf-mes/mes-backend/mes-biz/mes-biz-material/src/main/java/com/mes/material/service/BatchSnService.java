package com.mes.material.service;

import com.mes.common.result.PageResult;
import com.mes.material.dto.form.BatchSnForm;
import com.mes.material.dto.vo.BatchSnVO;

/**
 * 批次/SN 档案服务（追溯数据入口）。
 */
public interface BatchSnService {

    /**
     * 登记批次/SN 档案（objNo 空=系统生成：BATCH 前缀 PC + 日期流水 / SN 前缀 SN + 流水）。
     *
     * @param form 表单
     * @return 档案 id
     */
    Long register(BatchSnForm form);

    /**
     * 分页查询。
     *
     * @param pageNum   页码
     * @param pageSize  页大小
     * @param keyword   批次号/SN 模糊
     * @param objType   BATCH/SN
     * @param materialId 物料过滤
     * @return 分页结果
     */
    PageResult<BatchSnVO> page(long pageNum, long pageSize, String keyword, String objType, Long materialId);
}

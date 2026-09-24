package com.mes.quality.service;

import com.mes.common.result.PageResult;
import com.mes.quality.dto.form.QcRejectForm;
import com.mes.quality.dto.vo.QcRejectVO;

/**
 * 不合格品处置单服务。
 */
public interface QcRejectService {

    /**
     * 创建处置单（数量累计不得超过检验单不合格数）。
     *
     * @param form 表单
     * @return 处置单 id
     */
    Long create(QcRejectForm form);

    /**
     * 分页查询。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param keyword  处置单号/检验单号模糊
     * @return 分页结果
     */
    PageResult<QcRejectVO> page(long pageNum, long pageSize, String keyword);
}

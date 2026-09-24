package com.mes.quality.service;

import com.mes.common.result.PageResult;
import com.mes.quality.dto.form.QcInspectForm;
import com.mes.quality.dto.form.QcInspectItemFillForm;
import com.mes.quality.dto.vo.QcInspectVO;

import java.util.List;

/**
 * 检验单服务（IQC/IPQC/FQC 三类共用一套单据流）。
 * <p>状态机：0 草稿（录入实测值）→ 1 已提交（汇总合格/不合格数与总结论，回传 ERP）。
 */
public interface QcInspectService {

    /**
     * 创建检验单（按模板带出项目行，草稿态）。
     *
     * @param form 表单
     * @return 检验单 id
     */
    Long create(QcInspectForm form);

    /**
     * 录入项目实测值（逐行判定上下限，仅草稿可录）。
     *
     * @param inspectId 检验单 id
     * @param fills     实测值列表
     */
    void fillValues(Long inspectId, List<QcInspectItemFillForm> fills);

    /**
     * 提交检验单：校验全部项目已录入，汇总数量与结论并回传 ERP。
     *
     * @param inspectId 检验单 id
     */
    void submit(Long inspectId);

    /**
     * 分页查询。
     *
     * @param pageNum     页码
     * @param pageSize    页大小
     * @param keyword     检验单号/来源单号模糊
     * @param inspectType IQC/IPQC/FQC
     * @param status      0/1
     * @return 分页结果
     */
    PageResult<QcInspectVO> page(long pageNum, long pageSize, String keyword, String inspectType, Integer status);

    /**
     * 详情（含项目行）。
     *
     * @param id 检验单 id
     * @return 详情 VO
     */
    QcInspectVO detail(Long id);
}

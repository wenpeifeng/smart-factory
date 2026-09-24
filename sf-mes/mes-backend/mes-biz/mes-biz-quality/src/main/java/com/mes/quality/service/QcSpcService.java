package com.mes.quality.service;

import com.mes.common.result.PageResult;
import com.mes.quality.dto.form.QcSpcRuleForm;
import com.mes.quality.dto.vo.QcSpcChartVO;
import com.mes.quality.dto.vo.QcSpcRuleVO;

/**
 * SPC 服务：规则维护、检验提交自动采样、判异、控制图数据。
 */
public interface QcSpcService {

    /**
     * 分页查询规则。
     *
     * @param pageNum    页码
     * @param pageSize   页大小
     * @param keyword    物料编码/项目名模糊
     * @param materialId 物料过滤
     * @return 分页结果
     */
    PageResult<QcSpcRuleVO> page(long pageNum, long pageSize, String keyword, Long materialId);

    /**
     * 创建/修改规则（materialId+itemName 唯一，存在即更新）。
     *
     * @param form 表单
     * @return 规则 id
     */
    Long save(QcSpcRuleForm form);

    /**
     * 删除规则。
     *
     * @param id 规则 id
     */
    void delete(Long id);

    /**
     * 启用/停用规则。
     *
     * @param id     规则 id
     * @param status 1/0
     */
    void changeStatus(Long id, Integer status);

    /**
     * 控制图数据：规则 + 中心线 + 最近 N 个样本（含判异标记）。
     *
     * @param ruleId 规则 id
     * @param limit  样本数（默认 50）
     * @return 图表 VO
     */
    QcSpcChartVO chart(Long ruleId, int limit);
}

package com.erp.qc.service;

import com.erp.common.result.PageResult;
import com.erp.qc.dto.form.QcInspectForm;
import com.erp.qc.dto.form.QcInspectSubmitForm;
import com.erp.qc.dto.query.QcInspectQuery;
import com.erp.qc.dto.vo.InspectPendingVO;
import com.erp.qc.dto.vo.QcInspectVO;
import com.erp.qc.dto.vo.QcStatisticsVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 检验单 Service：手工/按来源单生成、实检值录入提交、来源单卡点查询与统计。
 */
public interface QcInspectService {

    /**
     * 分页查询检验单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<QcInspectVO> page(QcInspectQuery query);

    /**
     * 检验单详情（含检验项目实检值明细）。
     *
     * @param id 检验单 ID
     * @return 检验单详情
     * @throws com.erp.common.exception.BusinessException 检验单不存在
     */
    QcInspectVO getDetail(Long id);

    /**
     * 手工新增检验单（草稿；明细未传时按 检验类型+物料分类 自动匹配启用模板带出检验项目）。
     *
     * @param form 检验单表单
     * @return 新检验单 ID
     * @throws com.erp.common.exception.BusinessException 物料不存在或模板匹配失败
     */
    Long create(QcInspectForm form);

    /**
     * 修改草稿检验单（明细非空时整体覆盖）。
     *
     * @param id   检验单 ID
     * @param form 检验单表单
     * @throws com.erp.common.exception.BusinessException 非草稿状态或物料不存在
     */
    void update(Long id, QcInspectForm form);

    /**
     * 提交检验（草稿→已检验）：录入实检值/单项判定/合格数/不合格数/结论/检验人/检验日期。
     * 口径：合格数+不合格数=抽检数；结论=1 ⇔ 不合格数=0。
     *
     * @param id   检验单 ID
     * @param form 提交表单
     * @throws com.erp.common.exception.BusinessException 非草稿/数量口径不一致/明细行不属于该单
     */
    void submit(Long id, QcInspectSubmitForm form);

    /**
     * 删除草稿检验单（逻辑删，明细一并删）。
     *
     * @param id 检验单 ID
     * @throws com.erp.common.exception.BusinessException 非草稿状态
     */
    void delete(Long id);

    /**
     * 挂点接口：按来源单自动生成检验单（读来源单明细带出物料行，按物料分类+检验类型匹配模板）。
     * 幂等：同一来源单下某物料已存在未删除检验单（含草稿/已检验）则跳过该物料。
     *
     * @param bizType 来源单据类型：pur_instock / mrp_work_order / sal_return
     * @param bizId   来源单据 ID
     * @return 本次新生成的检验单 ID 列表（全部已存在时为空列表）
     * @throws com.erp.common.exception.BusinessException 来源单类型不合法或来源单不存在
     */
    List<Long> generateFromBiz(String bizType, Long bizId);

    /**
     * 挂点接口：查询来源单是否还有未完成（草稿）检验。
     * 一期单据审核前调用，pending=true 时按约定应阻止审核。
     *
     * @param bizType 来源单据类型：pur_instock / mrp_work_order / sal_return
     * @param bizId   来源单据 ID
     * @return 卡点结果（含未完成检验单列表）
     * @throws com.erp.common.exception.BusinessException 来源单类型不合法或来源单不存在
     */
    InspectPendingVO pendingOfSource(String bizType, Long bizId);

    /**
     * 质量统计：按物料/供应商合格率 + 检验单量趋势（仅已检验单，按检验日期区间）。
     *
     * @param from 区间起（含，空默认 30 天前）
     * @param to   区间止（含，空默认今天）
     * @return 统计结果
     */
    QcStatisticsVO statistics(LocalDate from, LocalDate to);
}

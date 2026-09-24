package com.erp.qc.service;

import com.erp.common.result.PageResult;
import com.erp.qc.dto.form.QcRejectForm;
import com.erp.qc.dto.query.QcRejectQuery;
import com.erp.qc.dto.vo.QcRejectVO;
import com.erp.qc.dto.vo.RejectSourceVO;

import java.util.List;

/**
 * 不合格品处置 Service。
 */
public interface QcRejectService {

    /**
     * 分页查询处置单。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<QcRejectVO> page(QcRejectQuery query);

    /**
     * 处置单详情。
     *
     * @param id 处置单 ID
     * @return 处置单详情
     * @throws com.erp.common.exception.BusinessException 处置单不存在
     */
    QcRejectVO getDetail(Long id);

    /**
     * 可生成处置单的检验单选项（已检验、不合格数>0、扣除已处置仍有余额）。
     *
     * @return 选项列表（按检验单号倒序，上限 200 条）
     */
    List<RejectSourceVO> listSources();

    /**
     * 新增处置单（校验：检验单已检验、不合格数>0、累计处置 ≤ 不合格数）。
     *
     * @param form 处置单表单
     * @return 新处置单 ID
     * @throws com.erp.common.exception.BusinessException 检验单状态/数量校验失败
     */
    Long create(QcRejectForm form);

    /**
     * 修改处置单（数量守卫排除自身后重算余额）。
     *
     * @param id   处置单 ID
     * @param form 处置单表单
     * @throws com.erp.common.exception.BusinessException 处置单不存在或数量校验失败
     */
    void update(Long id, QcRejectForm form);

    /**
     * 删除处置单（逻辑删，删除后占用余额自动释放）。
     *
     * @param id 处置单 ID
     * @throws com.erp.common.exception.BusinessException 处置单不存在
     */
    void delete(Long id);
}

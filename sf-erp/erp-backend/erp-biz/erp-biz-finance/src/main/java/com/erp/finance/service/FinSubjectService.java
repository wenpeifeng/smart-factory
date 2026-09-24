package com.erp.finance.service;

import com.erp.finance.dto.form.SubjectForm;
import com.erp.finance.dto.vo.SubjectVO;

import java.util.List;

/**
 * 会计科目服务：科目树维护（增改删查）。
 * <p>
 * 关键约束：凭证分录只能挂末级科目；科目被分录引用后不允许删除。
 */
public interface FinSubjectService {

    /**
     * 查询科目列表（按编码升序，可按编码/名称模糊过滤）。
     *
     * @param keyword 编码或名称关键词（可空）
     * @return 科目 VO 列表（含 leaf 末级标识）
     */
    List<SubjectVO> list(String keyword);

    /**
     * 新增科目。
     *
     * @param form 科目表单
     * @throws com.erp.common.exception.BusinessException 编码重复 / 上级科目不存在
     */
    void create(SubjectForm form);

    /**
     * 修改科目。
     *
     * @param id   科目 ID
     * @param form 科目表单
     * @throws com.erp.common.exception.BusinessException 科目不存在 / 编码重复 / 上级科目非法（自引用或挂到自己的子孙级）
     */
    void update(Long id, SubjectForm form);

    /**
     * 删除科目（逻辑删除）。
     *
     * @param id 科目 ID
     * @throws com.erp.common.exception.BusinessException 存在下级科目 / 被凭证分录引用
     */
    void delete(Long id);
}

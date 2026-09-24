package com.erp.finance.service;

import com.erp.finance.dto.form.BookForm;
import com.erp.finance.dto.vo.BookVO;

import java.util.List;

/**
 * 账套档案服务：多账套 CRUD + 新账套科目树复制。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>默认账套（is_default=1）不可删除；</li>
 *   <li>已有凭证的账套不可删除（防止误删业务数据）；</li>
 *   <li>账套编码 BS+流水由系统生成（与账套 ID 对齐，如 BS1003），不可修改。</li>
 * </ul>
 */
public interface FinBookService {

    /**
     * 账套列表（全部账套，按 ID 升序；账套档案本身不分账套隔离）。
     *
     * @return 账套 VO 列表
     */
    List<BookVO> list();

    /**
     * 新增账套（编码自动生成 BS+ID 流水）。
     *
     * @param form 账套表单
     * @return 新账套 ID
     * @throws com.erp.common.exception.BusinessException 名称重复等校验失败
     */
    Long create(BookForm form);

    /**
     * 修改账套（名称/期间政策/本位币/状态/备注；编码与默认标记不可改）。
     *
     * @param id   账套 ID
     * @param form 账套表单
     * @throws com.erp.common.exception.BusinessException 账套不存在
     */
    void update(Long id, BookForm form);

    /**
     * 删除账套（逻辑删除）。
     *
     * @param id 账套 ID
     * @throws com.erp.common.exception.BusinessException 默认账套 / 已有凭证的账套禁止删除
     */
    void delete(Long id);

    /**
     * 复制科目树到目标账套（新账套初始化科目用，幂等：已存在的编码跳过）。
     * <p>
     * 按层级逐级复制并重挂 parent_id（源账套科目 ID 在目标账套中不可复用）。
     *
     * @param toBookId   目标账套 ID
     * @param fromBookId 源账套 ID（可空 = 默认账套 1001）
     * @return 实际新增的科目数量（目标账套已有同编码科目时跳过）
     * @throws com.erp.common.exception.BusinessException 账套不存在 / 源目标相同
     */
    Integer copySubjects(Long toBookId, Long fromBookId);
}

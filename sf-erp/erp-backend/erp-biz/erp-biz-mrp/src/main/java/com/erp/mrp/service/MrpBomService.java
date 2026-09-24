package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.BomCopyForm;
import com.erp.mrp.dto.form.BomForm;
import com.erp.mrp.dto.vo.BomTreeVO;
import com.erp.mrp.dto.vo.BomVO;
import com.erp.mrp.entity.MrpBom;

/**
 * BOM 物料清单服务 —— CRUD、复制新版本、审核/停用、多级树形展开。
 */
public interface MrpBomService {

    /**
     * 分页查询 BOM（keyword 按成品物料编码/名称过滤）。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  物料编码/名称关键词，可空
     * @param status   状态过滤，可空
     * @return BOM 分页（含物料回显与子件数）
     */
    PageResult<BomVO> page(long pageNum, long pageSize, String keyword, String status);

    /**
     * BOM 详情（头 + 子件明细）。
     *
     * @param id BOMID
     * @return 详情 VO
     * @throws com.erp.common.exception.BusinessException 数据不存在
     */
    BomVO detail(Long id);

    /**
     * 新建 BOM（草稿状态）。
     *
     * @param form 表单
     * @return 新 BOMID
     * @throws com.erp.common.exception.BusinessException 物料不存在 / 版本重复 / 子件非法
     */
    Long create(BomForm form);

    /**
     * 修改 BOM（仅草稿可改）。
     *
     * @param id   BOMID
     * @param form 表单
     * @throws com.erp.common.exception.BusinessException 非草稿状态 / 校验失败
     */
    void update(Long id, BomForm form);

    /**
     * 删除 BOM（逻辑删除，仅草稿/停用且未被生产订单引用）。
     *
     * @param id BOMID
     * @throws com.erp.common.exception.BusinessException 已审核被引用等不可删场景
     */
    void delete(Long id);

    /**
     * 复制 BOM 为新版本（草稿状态）。
     *
     * @param id   源 BOMID
     * @param form 新版本号表单
     * @return 新 BOMID
     * @throws com.erp.common.exception.BusinessException 新版本号已存在
     */
    Long copy(Long id, BomCopyForm form);

    /**
     * 审核 BOM：DRAFT → ACTIVE（仅含子件的 BOM 可审核）。
     *
     * @param id BOMID
     * @throws com.erp.common.exception.BusinessException 状态不合法 / 无子件
     */
    void audit(Long id);

    /**
     * 停用 BOM：ACTIVE → DISABLED。
     *
     * @param id BOMID
     * @throws com.erp.common.exception.BusinessException 状态不合法
     */
    void disable(Long id);

    /**
     * BOM 多级树形展开（递归取子件的有效 BOM，深度上限 15，检测循环引用）。
     *
     * @param id BOMID
     * @return 树根节点（成品）
     * @throws com.erp.common.exception.BusinessException 循环引用 / 层级超限
     */
    BomTreeVO tree(Long id);

    /**
     * 取物料最新有效（ACTIVE）BOM。
     *
     * @param materialId 物料ID
     * @return 有效 BOM，无则 null
     */
    MrpBom findLatestActive(Long materialId);
}

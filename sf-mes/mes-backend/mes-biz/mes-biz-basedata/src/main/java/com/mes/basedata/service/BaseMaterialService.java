package com.mes.basedata.service;

import com.mes.basedata.dto.form.BaseMaterialForm;
import com.mes.basedata.dto.query.MaterialPageQuery;
import com.mes.basedata.dto.vo.BaseMaterialVO;
import com.mes.basedata.dto.vo.MaterialImportResultVO;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.common.result.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.Writer;
import java.util.Collection;
import java.util.List;

/**
 * 物料 Service —— 本模块对外（采购/销售/库存/MRP）最核心的共享能力。
 * <p>
 * 对外能力（跨模块只允许调 Service，禁止直查表）：
 * <ul>
 *   <li>{@link #getByIds(Collection)}：单据明细批量带出物料；</li>
 *   <li>{@link #listEnabled(String)}：单据选择器搜索启用物料；</li>
 *   <li>{@link #getEntityById(Long)}：校验物料存在性与启用状态。</li>
 * </ul>
 */
public interface BaseMaterialService {

    /**
     * 分页查询物料（按编码/名称关键词、分类含子孙、状态筛选）。
     *
     * @param query 查询条件
     * @return 分页结果（VO 冗余分类名/单位名）
     */
    PageResult<BaseMaterialVO> page(MaterialPageQuery query);

    /**
     * 按 ID 查详情。
     *
     * @param id 物料 ID
     * @return 物料 VO
     * @throws com.mes.common.exception.BusinessException DATA_NOT_EXISTS 若不存在
     */
    BaseMaterialVO getById(Long id);

    /**
     * 新增物料（编码留空时自动生成 WL+日期+3位流水）。
     *
     * @param form 物料表单
     * @return 新增后的 ID
     */
    Long create(BaseMaterialForm form);

    /**
     * 修改物料。
     *
     * @param id   物料 ID
     * @param form 物料表单
     */
    void update(Long id, BaseMaterialForm form);

    /**
     * 启用/停用物料。
     *
     * @param id     物料 ID
     * @param status 目标状态（1 启用 / 0 停用）
     */
    void changeStatus(Long id, Integer status);

    /**
     * 删除物料（逻辑删除）。
     * <p>单据模块（采购/销售/库存）上线后需在此补充引用校验并抛 DATA_IN_USE。
     *
     * @param id 物料 ID
     */
    void delete(Long id);

    /**
     * 批量按 ID 查实体（跨模块单据带出物料信息用）。
     *
     * @param ids 物料 ID 集合（空集合返回空列表）
     * @return 物料实体列表（不存在的 ID 自动跳过）
     */
    List<BaseMaterial> getByIds(Collection<Long> ids);

    /**
     * 启用物料搜索（单据行选择器下拉）。
     *
     * @param keyword 编码/名称关键词（可空，空则返回前 50 条）
     * @return 启用物料 VO 列表（最多 50 条）
     */
    List<BaseMaterialVO> listEnabled(String keyword);

    /**
     * 按条件导出 CSV（与分页同条件，最多 10000 行防呆）。
     *
     * @param query  导出条件
     * @param writer 响应输出流（由 Controller 传入）
     */
    void exportCsv(MaterialPageQuery query, Writer writer);

    /**
     * 下载导入模板 CSV。
     *
     * @param writer 响应输出流
     */
    void downloadTemplate(Writer writer);

    /**
     * 导入 CSV（逐行校验、错误跳过，部分成功模式）。
     *
     * @param file 上传的 CSV 文件
     * @return 导入结果（成功/失败计数与错误明细）
     */
    MaterialImportResultVO importCsv(MultipartFile file);

    /**
     * 按 ID 取实体并校验存在（跨模块校验物料存在性用）。
     *
     * @param id 物料 ID
     * @return 物料实体
     * @throws com.mes.common.exception.BusinessException DATA_NOT_EXISTS 若不存在
     */
    BaseMaterial getEntityById(Long id);
}

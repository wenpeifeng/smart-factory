package com.erp.basedata.service;

import com.erp.basedata.dto.form.PriceRecordForm;
import com.erp.basedata.dto.vo.PriceCompareRowVO;
import com.erp.basedata.dto.vo.PriceRecordVO;
import com.erp.basedata.dto.vo.PriceRefVO;
import com.erp.common.result.PageResult;

import java.util.List;

/**
 * 采购价格库 Service（价格流水 + 录单参照查询 + 扫描器沉淀）。
 * <p>
 * 采购订单录入的三个参照：last（合同价优先 + 最近成交价）、history（日期倒序可追溯）、
 * compare（多供应商最低/最高/均价对比）。
 */
public interface PurPriceService {

    /**
     * 价格流水分页（价格中心"采购价格流水"Tab）。
     *
     * @param pageNum    页码
     * @param pageSize   页大小
     * @param supplierId 供应商（可空）
     * @param materialId 物料（可空）
     * @param sourceType 来源类型 ORDER/INSTOCK/RFQ/CONTRACT（可空）
     * @return 价格流水分页（日期倒序）
     */
    PageResult<PriceRecordVO> page(long pageNum, long pageSize, Long supplierId, Long materialId, String sourceType);

    /**
     * 录单参照：合同价（生效中）优先展示 + 最近一次成交价。
     *
     * @param supplierId 供应商 ID
     * @param materialId 物料 ID
     * @return 参照结果（contract/last 均可能为 null）
     */
    PriceRefVO lastRef(Long supplierId, Long materialId);

    /**
     * 历史价分页（日期倒序，含来源单号可追溯）。
     *
     * @param materialId 物料 ID
     * @param supplierId 供应商（可空=该物料全部供应商）
     * @param pageNum    页码
     * @param pageSize   页大小（上限 50）
     * @return 价格流水分页
     */
    PageResult<PriceRecordVO> history(Long materialId, Long supplierId, long pageNum, long pageSize);

    /**
     * 多供应商同物料价格对比（最低/最高/均价/最近价）。
     *
     * @param materialId 物料 ID
     * @return 供应商维度聚合行（按最近价升序）
     */
    List<PriceCompareRowVO> compare(Long materialId);

    /**
     * 手工补录价格流水（历史导入/合同价；sourceType 落 CONTRACT）。
     *
     * @param form 补录表单
     * @return 新增 ID
     */
    Long createManual(PriceRecordForm form);

    /**
     * 删除价格流水（逻辑删除；扫描器不会重复沉淀已删除行）。
     *
     * @param id 流水 ID
     */
    void delete(Long id);

    /**
     * 扫描器单轮沉淀：已审核采购订单 + 采购入库 + 定标 RFQ → 价格流水。
     * <p>
     * 幂等（uk source_type+source_detail_id + NOT EXISTS 预筛）；
     * 单类单轮上限 500 行，多轮调度自然消化积压。
     *
     * @return 本轮新增流水条数
     */
    int collectOnce();
}

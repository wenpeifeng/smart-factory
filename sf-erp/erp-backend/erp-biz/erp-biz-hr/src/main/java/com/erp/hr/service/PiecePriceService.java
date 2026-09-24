package com.erp.hr.service;

import com.erp.common.result.PageResult;
import com.erp.hr.dto.form.PiecePriceForm;
import com.erp.hr.dto.vo.PiecePriceVO;

/**
 * 计件价目服务 —— 物料×工序单价的 CRUD（制造业计件工资的定价基础）。
 */
public interface PiecePriceService {

    /** 分页查询价目（keyword 匹配物料编码/名称或工序名） */
    PageResult<PiecePriceVO> page(long pageNum, long pageSize, String keyword, String status);

    /** 价目详情 */
    PiecePriceVO detail(Long id);

    /** 新增价目（同物料同工序唯一） */
    Long create(PiecePriceForm form);

    /** 修改价目 */
    void update(Long id, PiecePriceForm form);

    /** 删除价目（逻辑删除） */
    void delete(Long id);
}

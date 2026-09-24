package com.erp.inventory.service;

import com.erp.common.result.PageResult;
import com.erp.inventory.dto.form.SerialOutForm;
import com.erp.inventory.dto.form.SerialRegisterForm;
import com.erp.inventory.dto.request.SerialQuery;
import com.erp.inventory.dto.vo.InvSerialVO;

import java.util.List;

/**
 * 序列号 Service（V422）—— 入库登记 / 出库核销 / 全程追溯查询。
 * <p>
 * 台账口径：inv_serial 只做追溯记录，不参与 inv_stock 数量过账
 * （数量账由 StockService 唯一维护）。
 */
public interface InvSerialService {

    /**
     * 入库登记（批量导入/逐条共用）：写 IN_STOCK 台账。
     * 序列号已存在（未删除）抛重复异常，整体回滚。
     *
     * @return 登记条数
     */
    int register(SerialRegisterForm form);

    /**
     * 出库核验：全部序列号必须存在且 IN_STOCK，任一不满足整体回滚；
     * 通过后 status=OUT、写 out_no。
     *
     * @return 核销条数
     */
    int writeOff(SerialOutForm form);

    /**
     * 报废：IN_STOCK → SCRAP。
     */
    int scrap(List<String> serialNos, String remark);

    /**
     * 台账分页（正查=序列号精确/前缀，反查=物料）。
     */
    PageResult<InvSerialVO> page(SerialQuery query);
}

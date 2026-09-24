package com.erp.inventory.service.impl;

import com.erp.common.exception.BusinessException;
import com.erp.inventory.dto.form.TransferForm;
import com.erp.inventory.service.StockService;
import com.erp.inventory.service.StockTransferService;
import com.erp.inventory.support.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 仓库调拨服务实现：多行一个事务，共用一个 TR 单号。
 */
@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl implements StockTransferService {

    private final StockService stockService;
    private final BizNoGenerator bizNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String transfer(TransferForm form) {
        if (form.getFromWarehouseId().equals(form.getToWarehouseId())) {
            throw BusinessException.ofKey("erp.inv.transfer-same-warehouse");
        }
        // 一个调拨号贯穿本单所有行，流水页按 biz_no 即可还原整次调拨（一行=两条 MOVE 流水）
        String trNo = bizNoGenerator.next("TR");
        for (TransferForm.Line line : form.getLines()) {
            // StockService 是代理调用，事务加入本方法事务（整单原子，任一行失败全部回滚）
            stockService.transfer(form.getFromWarehouseId(), form.getToWarehouseId(),
                    line.getMaterialId(), line.getQty(), trNo);
        }
        return trNo;
    }
}

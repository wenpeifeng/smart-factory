package com.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.inventory.dto.form.SerialOutForm;
import com.erp.inventory.dto.form.SerialRegisterForm;
import com.erp.inventory.dto.request.SerialQuery;
import com.erp.inventory.dto.vo.InvSerialVO;
import com.erp.inventory.entity.InvSerial;
import com.erp.inventory.mapper.InvSerialMapper;
import com.erp.inventory.service.InvSerialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 序列号服务实现（V422）。
 * <p>
 * 台账口径：登记/核销/报废只写 inv_serial，不触发 inv_stock 数量过账——
 * 数量账由对应出入库单审核经 StockService 过账，序列号动作与之独立
 * （核对由人工/后续对账任务负责，避免双重记账）。
 * 唯一性：serial_no 全局唯一（uk + 服务端先查后插双保险，重复整体回滚）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvSerialServiceImpl implements InvSerialService {

    private final InvSerialMapper serialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int register(SerialRegisterForm form) {
        List<String> serialNos = form.getSerialNos().stream().map(String::trim).distinct().toList();
        if (serialNos.isEmpty()) {
            throw BusinessException.ofKey("erp.inv.sn-list-empty");
        }
        // 先查后插双保险（uk 兜底并发），任一重复整体回滚
        List<InvSerial> existed = serialMapper.selectBySerialNos(serialNos);
        if (!existed.isEmpty()) {
            throw BusinessException.ofKey("erp.inv.sn-already-registered", String.join(",", existed.stream().limit(5).map(InvSerial::getSerialNo).toList()), existed.size());
        }
        for (String no : serialNos) {
            InvSerial serial = new InvSerial();
            serial.setSerialNo(no);
            serial.setMaterialId(form.getMaterialId());
            serial.setWarehouseId(form.getWarehouseId());
            serial.setBatchNo(form.getBatchNo() == null ? "" : form.getBatchNo().trim());
            serial.setStatus(InvSerial.STATUS_IN_STOCK);
            serial.setSourceNo(form.getSourceNo());
            serialMapper.insert(serial);
        }
        log.info("[序列号][登记] 物料={} 仓库={} 来源单={} 共 {} 条",
                form.getMaterialId(), form.getWarehouseId(), form.getSourceNo(), serialNos.size());
        return serialNos.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int writeOff(SerialOutForm form) {
        List<String> serialNos = form.getSerialNos().stream().map(String::trim).distinct().toList();
        if (serialNos.isEmpty()) {
            throw BusinessException.ofKey("erp.inv.sn-list-empty2");
        }
        List<InvSerial> rows = serialMapper.selectBySerialNos(serialNos);
        if (rows.size() != serialNos.size()) {
            Set<String> found = new HashSet<>();
            rows.forEach(r -> found.add(r.getSerialNo()));
            String missing = String.join(",", serialNos.stream().filter(n -> !found.contains(n)).limit(5).toList());
            throw BusinessException.ofKey("erp.inv.sn-not-registered", missing, serialNos.size() - rows.size());
        }
        for (InvSerial row : rows) {
            if (!InvSerial.STATUS_IN_STOCK.equals(row.getStatus())) {
                throw BusinessException.ofKey("erp.inv.sn-status-not-in-stock", row.getSerialNo(), row.getStatus());
            }
        }
        for (InvSerial row : rows) {
            row.setStatus(InvSerial.STATUS_OUT);
            row.setOutNo(form.getOutNo());
            serialMapper.updateById(row);
        }
        log.info("[序列号][核销] 出库单={} 共 {} 条", form.getOutNo(), rows.size());
        return rows.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scrap(List<String> serialNos, String remark) {
        if (serialNos == null || serialNos.isEmpty()) {
            throw BusinessException.ofKey("erp.inv.sn-list-empty2");
        }
        List<String> nos = serialNos.stream().map(String::trim).distinct().toList();
        List<InvSerial> rows = serialMapper.selectBySerialNos(nos);
        if (rows.size() != nos.size()) {
            throw BusinessException.ofKey("erp.inv.sn-partial-not-registered", nos.size() - rows.size());
        }
        int count = 0;
        for (InvSerial row : rows) {
            if (!InvSerial.STATUS_IN_STOCK.equals(row.getStatus())) {
                continue;
            }
            row.setStatus(InvSerial.STATUS_SCRAP);
            row.setRemark(remark);
            serialMapper.updateById(row);
            count++;
        }
        log.info("[序列号][报废] 共 {} 条", count);
        return count;
    }

    @Override
    public PageResult<InvSerialVO> page(SerialQuery query) {
        IPage<InvSerialVO> page = serialMapper.selectSerialPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}

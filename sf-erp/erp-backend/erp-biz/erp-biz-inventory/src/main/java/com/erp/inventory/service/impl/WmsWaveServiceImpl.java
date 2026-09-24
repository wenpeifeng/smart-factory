package com.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.inventory.dto.form.WaveCreateForm;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.OutboundDocVO;
import com.erp.inventory.dto.vo.WmsTaskVO;
import com.erp.inventory.dto.vo.WmsWaveVO;
import com.erp.inventory.entity.WmsTask;
import com.erp.inventory.entity.WmsWave;
import com.erp.inventory.mapper.WmsTaskMapper;
import com.erp.inventory.mapper.WmsWaveMapper;
import com.erp.inventory.service.WmsTaskService;
import com.erp.inventory.service.WmsWaveService;
import com.erp.inventory.support.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WMS 波次服务实现（V421）。
 * <p>
 * 建波 = 逐张出库单调 {@link WmsTaskService#generatePickTasksForDoc} 生成拣选任务
 * 并回填 wave_id；拣选路径序 = 波内任务按库位编码升序（最简路径，不做巷道级优化）。
 */
@Service
@RequiredArgsConstructor
public class WmsWaveServiceImpl implements WmsWaveService {

    @Value("${wms.auto-task.days-back:7}")
    private int scanDaysBack;

    private final WmsWaveMapper waveMapper;
    private final WmsTaskMapper taskMapper;
    private final WmsTaskService taskService;
    private final BizNoGenerator bizNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WaveCreateForm form) {
        WmsWave wave = new WmsWave();
        wave.setWaveNo(bizNoGenerator.next("W"));
        wave.setWarehouseId(form.getWarehouseId());
        wave.setStatus(WmsWave.STATUS_CREATED);
        wave.setRemark(form.getRemark());
        waveMapper.insert(wave);

        int tasks = 0;
        for (WaveCreateForm.DocRef doc : form.getDocs()) {
            String sourceType = doc.getSourceType() == null ? "" : doc.getSourceType().trim();
            if (!"SAL_OUT".equals(sourceType) && !"OTH_OUT".equals(sourceType)) {
                throw BusinessException.ofKey("erp.inv.wave-source-unsupported", sourceType);
            }
            tasks += taskService.generatePickTasksForDoc(sourceType, doc.getDocId(), wave.getId());
        }
        if (tasks == 0) {
            throw BusinessException.ofKey("erp.inv.no-pickable-lines");
        }
        return wave.getId();
    }

    @Override
    public PageResult<WmsWaveVO> page(WmsTaskQuery query) {
        IPage<WmsWaveVO> page = waveMapper.selectWavePage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public WmsWaveVO detail(Long id) {
        WmsWave wave = requireWave(id);
        WmsWaveVO vo = new WmsWaveVO();
        vo.setId(wave.getId());
        vo.setWaveNo(wave.getWaveNo());
        vo.setWarehouseId(wave.getWarehouseId());
        vo.setStatus(wave.getStatus());
        vo.setFinishTime(wave.getFinishTime());
        vo.setRemark(wave.getRemark());
        vo.setCreateTime(wave.getCreateTime());
        // 头信息 + 波内任务进度（复用拣选路径序查询，一并计数）
        List<WmsTaskVO> tasks = taskMapper.selectWaveTasks(id);
        vo.setTaskCount((long) tasks.size());
        vo.setDoneCount(tasks.stream().filter(t -> t.getStatus() != null && t.getStatus() == WmsTask.STATUS_DONE).count());
        vo.setTotalQty(tasks.stream().map(WmsTaskVO::getQty)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        return vo;
    }

    @Override
    public List<WmsTaskVO> pickPath(Long id) {
        requireWave(id);
        return taskMapper.selectWaveTasks(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        WmsWave wave = requireWave(id);
        if (wave.getStatus() == WmsWave.STATUS_DONE) {
            throw BusinessException.ofKey("erp.inv.wave-done", wave.getWaveNo());
        }
        List<WmsTaskVO> tasks = taskMapper.selectWaveTasks(id);
        long unfinished = tasks.stream()
                .filter(t -> t.getStatus() == null || t.getStatus() != WmsTask.STATUS_DONE).count();
        if (unfinished > 0) {
            throw BusinessException.ofKey("erp.inv.wave-has-open-tasks", wave.getWaveNo(), unfinished);
        }
        if (tasks.isEmpty()) {
            throw BusinessException.ofKey("erp.inv.wave-no-tasks", wave.getWaveNo());
        }
        wave.setStatus(WmsWave.STATUS_DONE);
        wave.setFinishTime(LocalDateTime.now());
        waveMapper.updateById(wave);
    }

    @Override
    public List<OutboundDocVO> outboundDocs(Long warehouseId) {
        return taskMapper.selectOutboundDocsForWave(warehouseId, scanDaysBack);
    }

    private WmsWave requireWave(Long id) {
        WmsWave wave = waveMapper.selectById(id);
        if (wave == null) {
            throw BusinessException.ofKey("erp.inv.wave-not-exists", id);
        }
        return wave;
    }
}

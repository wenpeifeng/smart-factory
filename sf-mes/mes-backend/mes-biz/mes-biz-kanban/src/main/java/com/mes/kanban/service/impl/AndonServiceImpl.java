package com.mes.kanban.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseWorkcenter;
import com.mes.basedata.mapper.BaseWorkcenterMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.kanban.dto.form.AndonForm;
import com.mes.kanban.dto.vo.AndonVO;
import com.mes.kanban.entity.KbAndon;
import com.mes.kanban.mapper.KbAndonMapper;
import com.mes.kanban.service.AndonService;
import com.mes.plan.service.MesNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 安灯服务实现。
 */
@Service
@RequiredArgsConstructor
public class AndonServiceImpl implements AndonService {

    private static final List<String> TYPES = List.of("DEVICE", "MATERIAL", "QUALITY", "OTHER");

    private final KbAndonMapper andonMapper;
    private final BaseWorkcenterMapper workcenterMapper;
    private final MesNoGenerator noGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long call(AndonForm form) {
        if (!TYPES.contains(form.getType())) {
            throw new BusinessException("安灯类型必须为 DEVICE/MATERIAL/QUALITY/OTHER");
        }
        KbAndon andon = new KbAndon();
        andon.setCallNo(noGenerator.next("AD"));
        andon.setWorkcenterId(form.getWorkcenterId());
        andon.setDeviceId(form.getDeviceId());
        andon.setType(form.getType());
        andon.setCallerName(form.getCallerName());
        andon.setMessage(form.getMessage());
        andon.setStatus("OPEN");
        andon.setCallTime(LocalDateTime.now());
        andonMapper.insert(andon);
        return andon.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void respond(Long id, String responder) {
        KbAndon andon = requireAndon(id);
        if (!"OPEN".equals(andon.getStatus())) {
            throw new BusinessException("仅待响应呼叫可响应，当前: %s", andon.getStatus());
        }
        andon.setStatus("RESPOND");
        andon.setResponder(responder);
        andon.setRespondTime(LocalDateTime.now());
        andonMapper.updateById(andon);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String closeRemark) {
        KbAndon andon = requireAndon(id);
        if ("CLOSED".equals(andon.getStatus())) {
            throw new BusinessException("呼叫已关闭");
        }
        andon.setStatus("CLOSED");
        andon.setCloseRemark(closeRemark);
        andon.setCloseTime(LocalDateTime.now());
        andonMapper.updateById(andon);
    }

    @Override
    public PageResult<AndonVO> page(long pageNum, long pageSize, String status, String type) {
        var wrapper = Wrappers.lambdaQuery(KbAndon.class)
                .eq(StringUtils.hasText(status), KbAndon::getStatus, status)
                .eq(StringUtils.hasText(type), KbAndon::getType, type)
                .orderByDesc(KbAndon::getId);
        PageResult<AndonVO> page = PageUtils.of(andonMapper, wrapper, pageNum, pageSize, AndonVO::from);
        if (!page.getRecords().isEmpty()) {
            List<Long> wcIds = page.getRecords().stream().map(AndonVO::getWorkcenterId)
                    .filter(java.util.Objects::nonNull).toList();
            Map<Long, String> names = wcIds.isEmpty() ? Map.of()
                    : workcenterMapper.selectBatchIds(wcIds).stream()
                            .collect(Collectors.toMap(BaseWorkcenter::getId, BaseWorkcenter::getName));
            page.getRecords().forEach(vo -> {
                if (vo.getWorkcenterId() != null) {
                    vo.setWorkcenterName(names.get(vo.getWorkcenterId()));
                }
            });
        }
        return page;
    }

    private KbAndon requireAndon(Long id) {
        KbAndon andon = andonMapper.selectById(id);
        if (andon == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "安灯呼叫不存在: %d".formatted(id));
        }
        return andon;
    }
}

package com.mes.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.dto.form.BaseMaterialForm;
import com.mes.basedata.dto.query.MaterialPageQuery;
import com.mes.basedata.dto.vo.BaseMaterialVO;
import com.mes.basedata.dto.vo.MaterialImportResultVO;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.entity.BaseMaterialCategory;
import com.mes.basedata.entity.BaseUnit;
import com.mes.basedata.mapper.BaseMaterialCategoryMapper;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.basedata.mapper.BaseUnitMapper;
import com.mes.basedata.service.BaseMaterialService;
import com.mes.basedata.service.BaseMaterialCategoryService;
import com.mes.basedata.service.BizCodeGenerator;
import com.mes.basedata.util.CsvUtils;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物料 Service 实现。
 * <p>包含分页（编码/名称/分类含子孙筛选）、编码自动生成（WL+日期+3位流水）、
 * CSV 导入导出（RFC 4180 + UTF-8 BOM）与跨模块共享查询能力。
 */
@Service
@RequiredArgsConstructor
public class BaseMaterialServiceImpl implements BaseMaterialService {

    /** 导出/选择器单次查询上限（防呆：避免全表导出拖垮服务） */
    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final int MAX_SELECTOR_ROWS = 50;

    /** CSV 表头（导入导出共用，顺序即列序，禁止随意调整） */
    private static final List<String> CSV_HEADER = List.of(
            "物料编码", "物料名称", "规格型号", "分类编码", "单位名称",
            "条码", "批次管理", "序列号管理", "安全库存", "参考成本价", "状态");

    private final BaseMaterialMapper materialMapper;
    private final BaseMaterialCategoryMapper categoryMapper;
    private final BaseUnitMapper unitMapper;
    private final BaseMaterialCategoryService categoryService;
    private final BizCodeGenerator codeGenerator;

    @Override
    public PageResult<BaseMaterialVO> page(MaterialPageQuery query) {
        var wrapper = buildWrapper(query).orderByDesc(BaseMaterial::getId);
        // 分类不存在时 buildWrapper 已注入不可能命中条件，直接短路返回空页
        if (query.getCategoryId() != null
                && categoryService.collectSelfAndDescendantIds(query.getCategoryId()).isEmpty()) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        PageResult<BaseMaterialVO> page = PageUtils.of(materialMapper, wrapper,
                query.getPageNum(), query.getPageSize(), BaseMaterialVO::from);
        fillNames(page.getRecords());
        return page;
    }

    @Override
    public BaseMaterialVO getById(Long id) {
        BaseMaterialVO vo = BaseMaterialVO.from(getEntityById(id));
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public Long create(BaseMaterialForm form) {
        BaseMaterial entity = new BaseMaterial();
        applyForm(entity, form, true);
        materialMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseMaterialForm form) {
        BaseMaterial entity = getEntityById(id);
        applyForm(entity, form, false);
        materialMapper.updateById(entity);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态取值只允许 0/1");
        }
        BaseMaterial entity = getEntityById(id);
        entity.setStatus(status);
        materialMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        getEntityById(id);
        // TODO 单据模块(采购/销售/库存/MRP-BOM)上线后：被单据引用时抛 DATA_IN_USE
        materialMapper.deleteById(id);
    }

    @Override
    public List<BaseMaterial> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return materialMapper.selectBatchIds(ids);
    }

    @Override
    public List<BaseMaterialVO> listEnabled(String keyword) {
        List<BaseMaterial> list = materialMapper.selectList(Wrappers.lambdaQuery(BaseMaterial.class)
                .eq(BaseMaterial::getStatus, 1)
                .and(notBlank(keyword), w -> w.like(BaseMaterial::getCode, keyword)
                        .or().like(BaseMaterial::getName, keyword))
                .orderByDesc(BaseMaterial::getId)
                .last("LIMIT " + MAX_SELECTOR_ROWS));
        List<BaseMaterialVO> vos = list.stream().map(BaseMaterialVO::from).toList();
        fillNames(vos);
        return vos;
    }

    @Override
    public void exportCsv(MaterialPageQuery query, Writer writer) {
        try {
            // 与分页同条件导出，上限 1 万行
            query.setPageNum(1);
            query.setPageSize(MAX_EXPORT_ROWS);
            List<BaseMaterial> list = materialMapper.selectList(buildWrapper(query)
                    .orderByDesc(BaseMaterial::getId));

            Map<Long, String> categoryNames = loadNames(list.stream()
                    .map(BaseMaterial::getCategoryId).filter(Objects::nonNull).toList(),
                    id -> categoryMapper.selectById(id).getName());
            Map<Long, String> unitNames = loadNames(list.stream()
                    .map(BaseMaterial::getUnitId).filter(Objects::nonNull).toList(),
                    id -> unitMapper.selectById(id).getName());

            writer.write(CsvUtils.BOM);
            writer.write(CsvUtils.escapeLine(CSV_HEADER));
            writer.write("\n");
            for (BaseMaterial m : list) {
                writer.write(CsvUtils.escapeLine(List.of(
                        m.getCode(),
                        m.getName(),
                        nullToEmpty(m.getSpec()),
                        m.getCategoryId() == null ? "" : categoryNames.getOrDefault(m.getCategoryId(), ""),
                        m.getUnitId() == null ? "" : unitNames.getOrDefault(m.getUnitId(), ""),
                        nullToEmpty(m.getBarcode()),
                        m.getBatchManaged() != null && m.getBatchManaged() == 1 ? "是" : "否",
                        m.getSerialManaged() != null && m.getSerialManaged() == 1 ? "是" : "否",
                        m.getSafetyStock() == null ? "0" : m.getSafetyStock().stripTrailingZeros().toPlainString(),
                        m.getRefCost() == null ? "0" : m.getRefCost().stripTrailingZeros().toPlainString(),
                        m.getStatus() != null && m.getStatus() == 1 ? "启用" : "停用")));
                writer.write("\n");
            }
            writer.flush();
        } catch (IOException e) {
            throw new BusinessException("CSV 导出失败: %s".formatted(e.getMessage()));
        }
    }

    @Override
    public void downloadTemplate(Writer writer) {
        try {
            writer.write(CsvUtils.BOM);
            writer.write(CsvUtils.escapeLine(CSV_HEADER));
            writer.write("\n");
            // 两行示例：留空编码=自动生成；分类编码/单位名称必须与系统一致
            writer.write(CsvUtils.escapeLine(List.of("", "热轧钢板", "Q235B 3.0*1250*2500", "CL", "千克",
                    "", "否", "否", "100", "3800.00", "启用")));
            writer.write("\n");
            writer.write(CsvUtils.escapeLine(List.of("", "成品电机", "YS8024-0.75KW", "CP", "个",
                    "6901234567890", "是", "否", "20", "260.50", "启用")));
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            throw new BusinessException("模板生成失败: %s".formatted(e.getMessage()));
        }
    }

    @Override
    public MaterialImportResultVO importCsv(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int success = 0;
        // 预载分类编码、单位名称映射，避免逐行查库
        Map<String, Long> categoryByCode = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseMaterialCategory::getCode, BaseMaterialCategory::getId, (a, b) -> a));
        Map<String, Long> unitByName = unitMapper.selectList(null).stream()
                .collect(Collectors.toMap(BaseUnit::getName, BaseUnit::getId, (a, b) -> a));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) {
                throw new BusinessException("CSV 文件为空");
            }
            int rowNo = 1;
            while ((line = reader.readLine()) != null) {
                rowNo++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    importRow(CsvUtils.parseLine(stripBom(line)), categoryByCode, unitByName);
                    success++;
                } catch (BusinessException e) {
                    errors.add("第%d行: %s".formatted(rowNo, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new BusinessException("CSV 文件读取失败: %s".formatted(e.getMessage()));
        }
        return new MaterialImportResultVO(success + errors.size(), success, errors.size(), errors);
    }

    @Override
    public BaseMaterial getEntityById(Long id) {
        BaseMaterial entity = materialMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "物料不存在: %d".formatted(id));
        }
        return entity;
    }

    /** 构建列表查询条件（分页与导出共用） */
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BaseMaterial> buildWrapper(
            MaterialPageQuery query) {
        var wrapper = Wrappers.lambdaQuery(BaseMaterial.class)
                .and(notBlank(query.getKeyword()), w -> w.like(BaseMaterial::getCode, query.getKeyword())
                        .or().like(BaseMaterial::getName, query.getKeyword()))
                .eq(query.getStatus() != null, BaseMaterial::getStatus, query.getStatus());
        if (query.getCategoryId() != null) {
            var ids = categoryService.collectSelfAndDescendantIds(query.getCategoryId());
            if (ids.isEmpty()) {
                // 无匹配分类时以不可能命中的条件返回空集
                wrapper.eq(BaseMaterial::getId, -1L);
            } else {
                wrapper.in(BaseMaterial::getCategoryId, ids);
            }
        }
        return wrapper;
    }

    /**
     * 导入单行：列序与 CSV_HEADER 一致。
     *
     * @throws BusinessException 行数据非法时抛出（由调用方收集为错误明细）
     */
    private void importRow(List<String> fields, Map<String, Long> categoryByCode, Map<String, Long> unitByName) {
        if (fields.size() < CSV_HEADER.size()) {
            throw new BusinessException("列数不足，应为 %d 列".formatted(CSV_HEADER.size()));
        }
        BaseMaterialForm form = new BaseMaterialForm();
        form.setCode(fields.get(0).trim());
        form.setName(fields.get(1).trim());
        form.setSpec(fields.get(2).trim());
        String categoryCode = fields.get(3).trim();
        if (!categoryCode.isEmpty()) {
            Long categoryId = categoryByCode.get(categoryCode);
            if (categoryId == null) {
                throw new BusinessException("分类编码不存在: %s".formatted(categoryCode));
            }
            form.setCategoryId(categoryId);
        }
        String unitName = fields.get(4).trim();
        if (!unitName.isEmpty()) {
            Long unitId = unitByName.get(unitName);
            if (unitId == null) {
                throw new BusinessException("单位名称不存在: %s".formatted(unitName));
            }
            form.setUnitId(unitId);
        }
        form.setBarcode(fields.get(5).trim());
        form.setBatchManaged("是".equals(fields.get(6).trim()) ? 1 : 0);
        form.setSerialManaged("是".equals(fields.get(7).trim()) ? 1 : 0);
        form.setSafetyStock(parseDecimal(fields.get(8).trim(), "安全库存"));
        form.setRefCost(parseDecimal(fields.get(9).trim(), "参考成本价"));
        form.setStatus("停用".equals(fields.get(10).trim()) ? 0 : 1);
        create(form);
    }

    /** 解析非负 Decimal，非法值转业务异常（导入行级错误） */
    private BigDecimal parseDecimal(String value, String field) {
        if (value.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal decimal = new BigDecimal(value);
            if (decimal.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("%s不能为负数".formatted(field));
            }
            return decimal;
        } catch (NumberFormatException e) {
            throw new BusinessException("%s不是合法数字: %s".formatted(field, value));
        }
    }

    /** 表单字段拷贝；create 模式下编码留空自动生成（先校验后取号，避免校验失败浪费 Redis 流水） */
    private void applyForm(BaseMaterial entity, BaseMaterialForm form, boolean isCreate) {
        checkRefExists("物料分类", form.getCategoryId(), id -> categoryMapper.selectById(id) != null ? true : null);
        checkRefExists("计量单位", form.getUnitId(), id -> unitMapper.selectById(id) != null ? true : null);
        String code = form.getCode() == null ? "" : form.getCode().trim();
        if (isCreate) {
            entity.setCode(code.isEmpty() ? codeGenerator.nextCode(BizCodeGenerator.PREFIX_MATERIAL) : code);
        } else if (!code.isEmpty()) {
            entity.setCode(code);
        }
        checkCodeUnique(entity.getCode(), entity.getId());
        entity.setName(form.getName().trim());
        entity.setSpec(form.getSpec());
        entity.setCategoryId(form.getCategoryId());
        entity.setUnitId(form.getUnitId());
        entity.setBarcode(form.getBarcode());
        entity.setBatchManaged(boolFlag(form.getBatchManaged()));
        entity.setSerialManaged(boolFlag(form.getSerialManaged()));
        entity.setSafetyStock(form.getSafetyStock() == null ? BigDecimal.ZERO : form.getSafetyStock());
        entity.setRefCost(form.getRefCost() == null ? BigDecimal.ZERO : form.getRefCost());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 物料编码唯一性校验（排除自身） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = materialMapper.selectCount(Wrappers.lambdaQuery(BaseMaterial.class)
                .eq(BaseMaterial::getCode, code)
                .ne(excludeId != null, BaseMaterial::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "物料编码已存在: %s".formatted(code));
        }
    }

    /** 引用对象存在性校验（分类/单位） */
    private void checkRefExists(String what, Long refId, Function<Long, Boolean> existsFn) {
        if (refId == null) {
            return;
        }
        if (!Boolean.TRUE.equals(existsFn.apply(refId))) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "%s不存在: %d".formatted(what, refId));
        }
    }

    /** 批量填充分类名/单位名（一次查询避免 N+1） */
    private void fillNames(List<BaseMaterialVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, String> categoryNames = loadNames(
                vos.stream().map(BaseMaterialVO::getCategoryId).filter(Objects::nonNull).toList(),
                id -> {
                    BaseMaterialCategory c = categoryMapper.selectById(id);
                    return c == null ? "" : c.getName();
                });
        Map<Long, String> unitNames = loadNames(
                vos.stream().map(BaseMaterialVO::getUnitId).filter(Objects::nonNull).toList(),
                id -> {
                    BaseUnit u = unitMapper.selectById(id);
                    return u == null ? "" : u.getName();
                });
        for (BaseMaterialVO vo : vos) {
            if (vo.getCategoryId() != null) {
                vo.setCategoryName(categoryNames.getOrDefault(vo.getCategoryId(), ""));
            }
            if (vo.getUnitId() != null) {
                vo.setUnitName(unitNames.getOrDefault(vo.getUnitId(), ""));
            }
        }
    }

    /** 按 ID 批量加载名称映射 */
    private Map<Long, String> loadNames(List<Long> ids, Function<Long, String> loader) {
        Map<Long, String> map = new HashMap<>();
        for (Long id : ids.stream().distinct().toList()) {
            map.put(id, loader.apply(id));
        }
        return map;
    }

    /** 0/1 标志位容错（null 默认 0） */
    private Integer boolFlag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 去除行首可能残留的 UTF-8 BOM */
    private static String stripBom(String line) {
        return line.startsWith(CsvUtils.BOM) ? line.substring(1) : line;
    }
}

package com.erp.finance.support;

import com.erp.finance.mapper.FinQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客商名称查询器（跨模块只读协作）。
 * <p>
 * 按并行协作约定：客户/供应商名称直接 SELECT base_customer / base_supplier
 * （不 import basedata 模块代码）。basedata 的 V10x 建表脚本与本模块并行开发，
 * 可能尚未落库 —— 首次查询遇到表不存在（BadSqlGrammarException）时记录标记并降级：
 * 名称返回 null，前端展示"客户#ID"，不影响应收应付主流程。对方表落库后自动恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerNameLookup {

    private final FinQueryMapper finQueryMapper;

    /** base_customer 表当前是否可用（false = 已确认缺失，跳过查询） */
    private volatile boolean customerTableAvailable = true;
    /** base_supplier 表当前是否可用 */
    private volatile boolean supplierTableAvailable = true;

    /**
     * 批量查询客户名称。
     *
     * @param ids 客户 ID 集合（空集合返回空 Map）
     * @return id -&gt; 名称；表未落库或无记录的 id 不出现在结果中
     */
    public Map<Long, String> customerNames(Collection<Long> ids) {
        if (ids.isEmpty() || !customerTableAvailable) {
            return Map.of();
        }
        try {
            return toMap(finQueryMapper.selectCustomerRows(List.copyOf(ids)));
        } catch (BadSqlGrammarException e) {
            customerTableAvailable = false;
            log.warn("base_customer 表尚未创建（basedata 模块 V10x 未落库），客户名称降级为空");
            return Map.of();
        }
    }

    /**
     * 批量查询供应商名称。
     *
     * @param ids 供应商 ID 集合（空集合返回空 Map）
     * @return id -&gt; 名称；表未落库或无记录的 id 不出现在结果中
     */
    public Map<Long, String> supplierNames(Collection<Long> ids) {
        if (ids.isEmpty() || !supplierTableAvailable) {
            return Map.of();
        }
        try {
            return toMap(finQueryMapper.selectSupplierRows(List.copyOf(ids)));
        } catch (BadSqlGrammarException e) {
            supplierTableAvailable = false;
            log.warn("base_supplier 表尚未创建（basedata 模块 V10x 未落库），供应商名称降级为空");
            return Map.of();
        }
    }

    /** 将 SELECT id, name 的行结果转为 id -&gt; name 映射 */
    private Map<Long, String> toMap(List<Map<String, Object>> rows) {
        Map<Long, String> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            Object name = row.get("name");
            if (id instanceof Number number) {
                result.put(number.longValue(), name == null ? null : name.toString());
            }
        }
        return result;
    }
}

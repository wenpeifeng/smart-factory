package com.erp.asset.dto.request;

/**
 * 资产卡片分页查询条件。
 *
 * @param categoryId 类别 ID（可空）
 * @param status     状态：IN_USE/IDLE/DISPOSED（可空）
 * @param deptId     使用部门 ID（可空）
 * @param keyword    关键词（资产编码/名称 模糊，可空）
 */
public record AssetQuery(Long categoryId, String status, Long deptId, String keyword) {
}

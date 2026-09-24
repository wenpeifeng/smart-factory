package com.mes.system.dto.menu;

import com.mes.system.entity.SysMenu;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点 VO。
 * <p>
 * 同时用于两种场景：
 * <ul>
 *   <li>菜单管理页：返回全部类型（目录/菜单/按钮）的完整树；</li>
 *   <li>GET /auth/menus：只返回当前用户可见的目录+菜单（按钮与敏感管理字段为 null）。</li>
 * </ul>
 */
@Data
public class MenuVO {

    /** 菜单 ID */
    private Long id;

    /** 上级 ID，0 为根 */
    private Long parentId;

    /** 名称 */
    private String name;

    /** 类型：1-目录 2-菜单 3-按钮 */
    private Integer type;

    /** 路由地址（不带斜杠） */
    private String path;

    /** 前端组件路径（相对 pages/） */
    private String component;

    /** 权限标识 */
    private String perms;

    /** 图标名 */
    private String icon;

    /** 显示顺序 */
    private Integer sort;

    /** 是否显示：1-显示 0-隐藏 */
    private Integer visible;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 子节点 */
    private List<MenuVO> children = new ArrayList<>();

    /**
     * 实体转 VO。
     *
     * @param menu 菜单实体
     * @return VO（children 为空列表，由树组装器填充）
     */
    public static MenuVO from(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setName(menu.getName());
        vo.setType(menu.getType());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setPerms(menu.getPerms());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        vo.setCreateTime(menu.getCreateTime());
        return vo;
    }
}

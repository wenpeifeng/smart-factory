-- =====================================================================
-- V22: 审批中心菜单与权限码
--
-- V901 只创建了工作流业务表，未向 sys_menu 注册 workflow:* 权限码，
-- 导致即使用户持有 super_admin 角色，接口的 @RequirePerm 仍然全部 403。
-- 本迁移补齐流程定义、我的待办、消息中心及操作级权限，
-- 并为内置超级管理员补齐角色关联。
-- =====================================================================

INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
VALUES
    (400,  0,   '审批中心',     1, 'workflow',          NULL,                     NULL,                    'BellOutlined', 970, 1, 1),
    (401,  400, '我的待办',     2, 'workflow/todo',     'workflow/todo/TodoList', 'workflow:todo:list',    NULL,             1, 1, 1),
    (402,  400, '流程定义',     2, 'workflow/defs',     'workflow/defs/DefList',  'workflow:def:list',     NULL,             2, 1, 1),
    (403,  400, '消息中心',     2, 'workflow/messages', 'workflow/msgs/MsgCenter','workflow:message:list',  NULL,             3, 1, 1),
    (411,  401, '流程发起',     3, NULL,                NULL,                     'workflow:todo:add',     NULL,             1, 1, 1),
    (412,  401, '流程审批',     3, NULL,                NULL,                     'workflow:todo:audit',   NULL,             2, 1, 1),
    (421,  402, '流程定义新增', 3, NULL,                NULL,                     'workflow:def:add',      NULL,             1, 1, 1),
    (422,  402, '流程定义修改', 3, NULL,                NULL,                     'workflow:def:edit',     NULL,             2, 1, 1),
    (423,  402, '流程定义删除', 3, NULL,                NULL,                     'workflow:def:delete',   NULL,             3, 1, 1),
    (431,  403, '消息标记已读', 3, NULL,                NULL,                     'workflow:message:edit', NULL,             1, 1, 1);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT `r`.`id`, `m`.`id`
FROM `sys_role` `r`
JOIN `sys_menu` `m` ON `m`.`id` BETWEEN 400 AND 431
WHERE `r`.`code` = 'super_admin'
  AND NOT EXISTS (
      SELECT 1
      FROM `sys_role_menu` `rm`
      WHERE `rm`.`role_id` = `r`.`id`
        AND `rm`.`menu_id` = `m`.`id`
  );

# 审批工作流模块（erp-workflow）设计说明

> 模块：erp-backend/erp-workflow + erp-web/src/pages/workflow
> SQL：script/sql/migration/V901__workflow_init.sql（版本段 V90x）
> 状态：开发完成，引擎闭环（定义→发起→条件路由→待办→通过/驳回→消息）curl 实测通过

## 1. 功能范围（对标金蝶"多级审批流"轻量版）

| 功能 | 表 | 说明 |
|---|---|---|
| 流程定义 | wf_definition / wf_node | 编码/名称/适用单据类型/启停用；节点子表整体提交（覆盖式保存） |
| 流程实例 | wf_instance / wf_task | 一单据一实例；任务分待办/已办；或签角色节点；条件路由跳过节点 |
| 消息中心 | wf_message | TODO 新待办 / APPROVE 通过 / REJECT 驳回 / WARN 预警；未读计数 |
| 预警任务 | （定时扫描，无独立表） | 每小时扫描库存安全线/客户信用/生产超期 → admin 发 WARN（Redis 24h 去重） |

适用单据类型（字典 `wf_biz_type`，V901 初始化）：
`pur_order` 采购订单 / `sal_order` 销售订单 / `pur_instock` 采购入库 / `sal_outstock` 销售出库 /
`mrp_work_order` 生产订单 / `fin_payment` 付款单 / `fin_receipt` 收款单。

## 2. 引擎语义（关键约定）

1. **匹配规则**：`startFlow` 按 bizType 匹配"启用"状态的定义，多条启用时取 **id 最大（最新启用）** 的一条；无启用流程 → 返回 `SKIPPED`，单据免审直接过。
2. **串行流转**：节点按 `node_order` 从小到大逐个推进；一个节点处理完才进入下一个。
3. **条件路由**：`condition_expr` 形如 `amount>10000`（支持 `> >= < <= == !=`，数值 BigDecimal 比较），变量取自**发起时**的 `vars`（JSON 冗余在实例上，单据后续修改不影响在途流程走向）。条件不满足 → **跳过该节点**；全部节点被跳过 → 实例即时 `AGREED`。表达式语法在定义保存时校验、求值失败（变量缺失/非数值）按不满足处理（宁可放行不卡单）。
4. **审批人**：`USER` 指定用户（1 条任务）；`APPROVER` 按角色（角色下每个启用用户各 1 条任务，**或签**——任一成员处理即推进，其余待办自动置 `CANCELLED`）。角色下无启用用户 → 发起失败回滚（不产生无待办的僵尸实例）。
5. **幂等**：同一 `bizType+bizId` 已有 `RUNNING` 实例时，重复 `startFlow` 直接返回该实例。
6. **并发防护**：任务/实例状态推进全部条件 UPDATE（`WHERE status='PENDING'` / `WHERE status='RUNNING'`），影响 0 行即报"已被处理"，事务回滚（与一期采购审核同一防重口径）。
7. **状态机**：实例 `RUNNING → AGREED/REJECTED/CANCELLED` 终态不可逆；`isApproved` 以**最新实例**状态判定（驳回后重新提交 = 新实例重新走流程）。
8. **快照**：任务冗余节点序号/名称，实例冗余流程变量 —— 定义后续修改/删除不影响在途流程展示与推进；存在 RUNNING 实例的定义禁止删除（先停用等走完）。

## 3. WorkflowService 签名（跨模块集成唯一入口）

```java
package com.erp.workflow.service;

public interface WorkflowService {

    /** 发起流程：SKIPPED无匹配启用流程(单据直接过) / RUNNING审批中 / AGREED条件全不满足即时通过 */
    StartResultVO startFlow(String bizType, Long bizId, String bizNo, Map<String, Object> vars);

    /** 单据是否审批通过：无实例(未接流程)=true；最新实例 AGREED=true，RUNNING/REJECTED/CANCELLED=false */
    boolean isApproved(String bizType, Long bizId);

    /** 通过当前任务（仅审批人本人）：推进或签兄弟任务失效；无下一节点 → 实例 AGREED */
    void approve(Long taskId, String opinion);

    /** 驳回当前任务（仅审批人本人）：实例整体 REJECTED，全部待办失效 */
    void reject(Long taskId, String opinion);

    /** 撤回实例（仅发起人，仅 RUNNING） */
    void cancel(Long instanceId);

    /** 我的待办/已办/我发起的 分页查询 */
    PageResult<WfTaskVO> todoPage(WfTaskQuery query);
    PageResult<WfTaskVO> donePage(WfTaskQuery query);
    PageResult<WfInstanceVO> myInstancePage(WfInstanceQuery query);
}
```

`StartResultVO`：`{ status: SKIPPED|RUNNING|AGREED, instanceId, message }`。

### 一期单据接线说明（约定，各单据模块自行接入）

单据"审核"动作改造为三段式（以采购订单 `PUT /purchase/orders/{id}/audit` 为例）：

```java
// PurOrderServiceImpl.audit(id) 内：
StartResultVO r = workflowService.startFlow(
        "pur_order", id, order.getOrderNo(), Map.of("amount", order.getTotalAmount()));
switch (r.getStatus()) {
    case "SKIPPED", "AGREED" -> doAudit(id);            // 无流程/即时通过 → 执行原审核逻辑（草稿→已审核）
    default                   -> markAuditing(id);       // RUNNING → 单据置"审批中"状态，挂待办
}

// 审批完成回调（建议在 erp-workflow 通知或单据模块轮询处）：
if (workflowService.isApproved("pur_order", id)) {
    doAudit(id);                                         // 审批通过后才真正执行原审核逻辑
}
```

接线要点：

1. 单据表需新增"审批中"状态值（各模块自己的迁移脚本，建议 5审批中），审核动作先 `startFlow`；
2. `vars` 约定以 `amount`（价税合计）为标准变量名，与节点条件表达式（`amount>10000`）对齐；
3. 被驳回（`isApproved=false` 且最新实例 REJECTED）时单据回到可修改状态，可重新提交（重新发起 = 新实例）；
4. 审批人处理入口统一在 /workflow/todo（前端已提供），单据模块无需自建审批 UI；
5. 本模块不反查单据表内容，单据展示由 /workflow/todo 返回的 `bizNo/bizType` 直达或前端按需跳单据详情页。

## 4. API 清单（context-path=/api）

### 流程定义 /workflow/defs
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /workflow/defs | 分页：pageNum/pageSize/keyword(编码/名称)/bizType/status |
| GET | /workflow/defs/{id} | 详情（含节点子表，按序号升序） |
| POST | /workflow/defs | 新增（flowCode/flowName/bizType/status/remark + nodes[]） |
| PUT | /workflow/defs/{id} | 修改（节点整体覆盖） |
| PUT | /workflow/defs/{id}/status | 启用/停用（body: {status:1/0}） |
| DELETE | /workflow/defs/{id} | 删除（逻辑删；存在 RUNNING 实例时拒绝） |

### 流程实例 /workflow/instances
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /workflow/instances | 发起：{bizType, bizId, bizNo, vars:{amount:...}} → StartResultVO |
| GET | /workflow/instances | 我发起的分页：keyword/bizType/status |
| PUT | /workflow/instances/{id}/cancel | 撤回（仅发起人，仅 RUNNING） |

### 审批任务 /workflow
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /workflow/todo | 我的待办分页（PENDING） |
| GET | /workflow/done | 我的已办分页（APPROVED/REJECTED/CANCELLED） |
| POST | /workflow/tasks/{id}/approve | 通过 {opinion?}；最后节点通过 → 实例 AGREED |
| POST | /workflow/tasks/{id}/reject | 驳回 {opinion?}；实例 REJECTED，发起人收消息 |

### 消息中心 /workflow/messages
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /workflow/messages | 分页：msgType/unreadOnly |
| GET | /workflow/messages/unread-count | 未读数（菜单角标） |
| PUT | /workflow/messages/{id}/read | 标记已读（仅本人） |
| PUT | /workflow/messages/read-all | 全部已读（返回标记条数） |

### 联调（一期单据接入前的引擎验证）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /workflow/demo/start | {bizType, amount} 时间戳模拟 bizId 发起，返回 start 结果 + isApproved |
| POST | /workflow/warn/run | 手动触发一次预警扫描（免等整点），返回新发预警条数 |

## 5. 消息与预警

- **消息触发**：新待办 → 下一审批人（TODO）；节点通过/全部通过 → 发起人（APPROVE）；驳回 → 发起人（REJECT）；预警 → admin（WARN，`biz_path` 指向对应页面）。
- **预警任务**：`WfWarnScheduleConfig` @Scheduled cron=`0 0 * * * *`（每小时整点），开关 `wf.warn.enabled`（默认 true，无需配置文件声明）：
  - 库存：`base_material.safety_stock > SUM(inv_stock.qty)`（全仓汇总，物料启用且安全库存>0）；
  - 信用：客户未收回应收 `SUM(fin_receivable.amount - settled_amount)`（status≠SETTLED）> `base_customer.credit_limit`（口径不含期初应收 init_receivable）；
  - 生产：`mrp_work_order` 状态 DRAFT/RELEASED/PRODUCING 且 `plan_end_date < 今天`。
- **去重**：Redis `SETNX erp:wf:warn:{type}:{bizId}` TTL 24h，同一对象 24h 只发一次。
- 跨模块查询全部走 `WfRefMapper`（@Select 只读投影，不 import 他人代码，与采购 BasedataMapper 同一约定）。

## 6. 前端

- `src/api/workflow.ts`：类型与请求（与后端 VO 对齐）；
- `src/pages/workflow/todo/TodoList.tsx`：待办/已办/我发起的 三 Tab；待办行内"通过/驳回"弹窗（驳回必填意见）；我发起的运行中可撤回；
- `src/pages/workflow/defs/DefList.tsx`：定义列表（启停 Switch/编辑/删除）+ Drawer 主子表编辑（节点 Form.List：序号/名称/审批人类型/审批人(角色|用户联动下拉)/条件表达式）；
- `src/pages/workflow/msgs/MsgCenter.tsx`：未读 Badge+加粗高亮、类型筛选、仅未读开关、点击已读+跳转、全部已读；
- `src/pages/workflow/shared.tsx`：单据类型/实例/任务/消息状态标签映射；
- `src/router/modules/workflow.tsx`：`workflowRoutes`（workflow/todo、workflow/defs、workflow/msgs），菜单 MainLayout 已预置；片段由主线程合并进 router/index.tsx。

## 7. 已知事项 / 遗留

1. **路由合并**：`router/index.tsx` 属公共文件，`workflowRoutes` 需主线程加一行 `...toRouteChildren(workflowRoutes)`（本任务不修改公共文件）。
2. **审批完成回调**：一期单据接入时需约定"实例 AGREED 后回调单据模块执行原审核逻辑"的机制（轮询 isApproved 或事件），当前由集成方在审核入口自行判断。
3. **表达式能力**：条件仅支持单段"变量 比较符 数值"（覆盖金额阈值场景）；如需 `a>1&&b<2` 组合条件再扩展 ConditionEvaluator（保持失败=跳过的语义）。
4. **预警接收人**：当前固定 admin（id=1）；如需按角色/多人分发，扩展 WfConstants.WARN_RECEIVER_ADMIN 为角色查询。
5. **消息保留策略**：暂无清理任务，量级可控；如需保留期限制后续加 @Scheduled 清理。

package com.erp.common.i18n;

/**
 * 消息 Key 常量 —— 后端国际化文案的唯一登记处（与
 * erp-bootstrap/src/main/resources/i18n/messages_zh_CN.properties、
 * messages_en.properties 一一对应）。
 * <p>
 * 命名规范（详见 docs/modules/i18n.md）：
 * <ul>
 *   <li>{@code erp.error.*}：框架层/错误码枚举（ErrorCode.codeKey）文案；</li>
 *   <li>{@code erp.biz.*}：跨模块高频业务提示（库存不足/请勿重复提交/期间已结账 等），
 *       业务代码 {@code throw BusinessException.ofKey(MessageKeys.XXX, args)} 引用；</li>
 *   <li>仅单模块使用的文案建议放各模块前缀（如 {@code erp.inv.*}），避免本类膨胀。</li>
 * </ul>
 * 注意：占位符用 MessageFormat 风格 {0}/{1}；英文文案含 ' 时需写成 ''（MessageFormat 转义）。
 * <p>
 * 本类为纯 Java 常量（erp-common 不依赖 Spring），消息解析由 erp-framework 的
 * {@code ErpMessages} 门面按请求 Locale（Accept-Language）完成。
 */
public final class MessageKeys {

    private MessageKeys() {
    }

    // ================= erp.error.* 框架层（与 ErrorCode.codeKey 一致） =================

    public static final String SUCCESS = "erp.error.success";
    public static final String BAD_REQUEST = "erp.error.bad-request";
    public static final String UNAUTHORIZED = "erp.error.unauthorized";
    public static final String FORBIDDEN = "erp.error.forbidden";
    public static final String NOT_FOUND = "erp.error.not-found";
    public static final String METHOD_NOT_ALLOWED = "erp.error.method-not-allowed";
    public static final String INTERNAL_ERROR = "erp.error.internal-error";
    public static final String SERVICE_UNAVAILABLE = "erp.error.service-unavailable";
    public static final String BUSINESS_ERROR = "erp.error.business-error";
    public static final String DUPLICATE_DATA = "erp.error.duplicate-data";
    public static final String DATA_NOT_EXISTS = "erp.error.data-not-exists";
    public static final String DATA_IN_USE = "erp.error.data-in-use";

    // ================= erp.error.* 框架处理器补充文案（带参数） =================

    /** 参数校验失败（{0}=具体字段错误拼接） */
    public static final String VALIDATION_FAILED = "erp.error.validation-failed";
    /** 缺少必填请求参数（{0}=参数名） */
    public static final String MISSING_PARAM = "erp.error.missing-param";
    /** 参数类型不匹配（{0}=参数名） */
    public static final String TYPE_MISMATCH = "erp.error.type-mismatch";
    /** 接口不存在（{0}=请求路径） */
    public static final String NOT_FOUND_DETAIL = "erp.error.not-found-detail";

    // ================= erp.biz.* 通用业务提示（高频文案） =================

    // ---- 认证 / 权限 ----
    /** 账号或密码错误 */
    public static final String BIZ_LOGIN_FAILED = "erp.biz.login-failed";
    /** 账号锁定（{0}=重试等待说明） */
    public static final String BIZ_ACCOUNT_LOCKED = "erp.biz.account-locked";
    /** 账号已停用 */
    public static final String BIZ_ACCOUNT_DISABLED = "erp.biz.account-disabled";
    /** 权限不足 */
    public static final String BIZ_PERMISSION_DENIED = "erp.biz.permission-denied";
    /** 未登录或登录已过期 */
    public static final String BIZ_NOT_LOGGED_IN = "erp.biz.not-logged-in";

    // ---- 通用数据 ----
    /** 数据不存在 */
    public static final String BIZ_DATA_NOT_EXISTS = "erp.biz.data-not-exists";
    /** 数据已存在，请勿重复提交 */
    public static final String BIZ_DATA_EXISTS = "erp.biz.data-exists";
    /** 请勿重复提交 */
    public static final String BIZ_DUPLICATE_SUBMIT = "erp.biz.duplicate-submit";
    /** 数据被引用，无法删除 */
    public static final String BIZ_DATA_IN_USE = "erp.biz.data-in-use";
    /** 编码已存在（{0}=编码值） */
    public static final String BIZ_CODE_EXISTS = "erp.biz.code-exists";
    /** 名称已存在（{0}=名称值） */
    public static final String BIZ_NAME_EXISTS = "erp.biz.name-exists";
    /** 并发冲突，请刷新后重试 */
    public static final String BIZ_CONCURRENT_CONFLICT = "erp.biz.concurrent-conflict";
    /** 保存失败 */
    public static final String BIZ_SAVE_FAILED = "erp.biz.save-failed";

    // ---- 单据通用 ----
    /** 单据不存在（{0}=单据编号或 id） */
    public static final String BIZ_DOC_NOT_EXISTS = "erp.biz.doc-not-exists";
    /** 单据已审核，请勿重复审核（{0}=单据编号） */
    public static final String BIZ_DOC_AUDITED = "erp.biz.doc-audited";
    /** 单据未审核，请先审核（{0}=单据编号） */
    public static final String BIZ_DOC_NOT_AUDITED = "erp.biz.doc-not-audited";
    /** 单据已审核过账，不允许删除/修改（{0}=单据编号） */
    public static final String BIZ_DOC_POSTED = "erp.biz.doc-posted";
    /** 单据无明细行，无法审核 */
    public static final String BIZ_DOC_NO_LINES = "erp.biz.doc-no-lines";
    /** 当前状态不允许该操作 */
    public static final String BIZ_STATUS_NOT_ALLOWED = "erp.biz.status-not-allowed";
    /** 期间已结账，不允许改动（{0}=期间） */
    public static final String BIZ_PERIOD_CLOSED = "erp.biz.period-closed";

    // ---- 库存 ----
    /** 库存不足（{0}=仓库，{1}=物料，{2}=数量说明） */
    public static final String BIZ_STOCK_NOT_ENOUGH = "erp.biz.stock-not-enough";
    /** 指定批次库存不足（{0}=仓库，{1}=物料，{2}=批次） */
    public static final String BIZ_BATCH_STOCK_NOT_ENOUGH = "erp.biz.batch-stock-not-enough";
    /** 数量必须大于 0 */
    public static final String BIZ_QTY_POSITIVE = "erp.biz.qty-positive";
    /** 调出仓与调入仓不能相同 */
    public static final String BIZ_SAME_WAREHOUSE = "erp.biz.same-warehouse";
    /** 启用批次管理，必须指定批次号（{0}=物料） */
    public static final String BIZ_BATCH_REQUIRED = "erp.biz.batch-required";
    /** 未启用批次管理，不能指定批次出库（{0}=物料） */
    public static final String BIZ_BATCH_NOT_ALLOWED = "erp.biz.batch-not-allowed";

    // ---- 通用操作 ----
    /** 操作过于频繁，请稍后再试 */
    public static final String BIZ_TOO_FREQUENT = "erp.biz.too-frequent";
    /** 导出失败 */
    public static final String BIZ_EXPORT_FAILED = "erp.biz.export-failed";
    /** 导入失败：文件格式错误 */
    public static final String BIZ_IMPORT_FORMAT_ERROR = "erp.biz.import-format-error";
    /** 已停用，不允许操作 */
    public static final String BIZ_ALREADY_DISABLED = "erp.biz.already-disabled";

    // ================= erp.biz.mrp-* 生产制造（V621 倒冲/替代料） =================
    /** 倒冲工单不允许手工领料 */
    public static final String MRP_BACKFLUSH_NO_MANUAL_ISSUE = "erp.biz.mrp-backflush-no-manual-issue";
    /** 主料及全部替代料库存仍不足 */
    public static final String MRP_SUB_STOCK_NOT_ENOUGH = "erp.biz.mrp-sub-stock-not-enough";
    /** 替代料不能与主料相同 */
    public static final String MRP_SUB_SAME_AS_MAIN = "erp.biz.mrp-sub-same-as-main";
    /** 同子件替代料重复 */
    public static final String MRP_SUB_DUPLICATE = "erp.biz.mrp-sub-duplicate";
    /** 非草稿 BOM 不允许维护替代料 */
    public static final String MRP_BOM_NOT_DRAFT_SUB = "erp.biz.mrp-bom-not-draft-sub";
}

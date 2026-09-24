package com.mes.common.i18n;

/**
 * 消息 Key 常量 —— 后端国际化文案的唯一登记处（与
 * mes-bootstrap/src/main/resources/i18n/messages_zh_CN.properties、
 * messages_en.properties 一一对应）。
 * <p>
 * 命名规范（详见 docs/modules/i18n.md）：
 * <ul>
 *   <li>{@code mes.error.*}：框架层/错误码枚举（ErrorCode.codeKey）文案；</li>
 *   <li>{@code mes.biz.*}：跨模块高频业务提示（库存不足/请勿重复提交/期间已结账 等），
 *       业务代码 {@code throw BusinessException.ofKey(MessageKeys.XXX, args)} 引用；</li>
 *   <li>仅单模块使用的文案建议放各模块前缀（如 {@code mes.inv.*}），避免本类膨胀。</li>
 * </ul>
 * 注意：占位符用 MessageFormat 风格 {0}/{1}；英文文案含 ' 时需写成 ''（MessageFormat 转义）。
 * <p>
 * 本类为纯 Java 常量（mes-common 不依赖 Spring），消息解析由 mes-framework 的
 * {@code MesMessages} 门面按请求 Locale（Accept-Language）完成。
 */
public final class MessageKeys {

    private MessageKeys() {
    }

    // ================= mes.error.* 框架层（与 ErrorCode.codeKey 一致） =================

    public static final String SUCCESS = "mes.error.success";
    public static final String BAD_REQUEST = "mes.error.bad-request";
    public static final String UNAUTHORIZED = "mes.error.unauthorized";
    public static final String FORBIDDEN = "mes.error.forbidden";
    public static final String NOT_FOUND = "mes.error.not-found";
    public static final String METHOD_NOT_ALLOWED = "mes.error.method-not-allowed";
    public static final String INTERNAL_ERROR = "mes.error.internal-error";
    public static final String SERVICE_UNAVAILABLE = "mes.error.service-unavailable";
    public static final String BUSINESS_ERROR = "mes.error.business-error";
    public static final String DUPLICATE_DATA = "mes.error.duplicate-data";
    public static final String DATA_NOT_EXISTS = "mes.error.data-not-exists";
    public static final String DATA_IN_USE = "mes.error.data-in-use";

    // ================= mes.error.* 框架处理器补充文案（带参数） =================

    /** 参数校验失败（{0}=具体字段错误拼接） */
    public static final String VALIDATION_FAILED = "mes.error.validation-failed";
    /** 缺少必填请求参数（{0}=参数名） */
    public static final String MISSING_PARAM = "mes.error.missing-param";
    /** 参数类型不匹配（{0}=参数名） */
    public static final String TYPE_MISMATCH = "mes.error.type-mismatch";
    /** 接口不存在（{0}=请求路径） */
    public static final String NOT_FOUND_DETAIL = "mes.error.not-found-detail";

    // ================= mes.biz.* 通用业务提示（高频文案） =================

    // ---- 认证 / 权限 ----
    /** 账号或密码错误 */
    public static final String BIZ_LOGIN_FAILED = "mes.biz.login-failed";
    /** 账号锁定（{0}=重试等待说明） */
    public static final String BIZ_ACCOUNT_LOCKED = "mes.biz.account-locked";
    /** 账号已停用 */
    public static final String BIZ_ACCOUNT_DISABLED = "mes.biz.account-disabled";
    /** 权限不足 */
    public static final String BIZ_PERMISSION_DENIED = "mes.biz.permission-denied";
    /** 未登录或登录已过期 */
    public static final String BIZ_NOT_LOGGED_IN = "mes.biz.not-logged-in";

    // ---- 通用数据 ----
    /** 数据不存在 */
    public static final String BIZ_DATA_NOT_EXISTS = "mes.biz.data-not-exists";
    /** 数据已存在，请勿重复提交 */
    public static final String BIZ_DATA_EXISTS = "mes.biz.data-exists";
    /** 请勿重复提交 */
    public static final String BIZ_DUPLICATE_SUBMIT = "mes.biz.duplicate-submit";
    /** 数据被引用，无法删除 */
    public static final String BIZ_DATA_IN_USE = "mes.biz.data-in-use";
    /** 编码已存在（{0}=编码值） */
    public static final String BIZ_CODE_EXISTS = "mes.biz.code-exists";
    /** 名称已存在（{0}=名称值） */
    public static final String BIZ_NAME_EXISTS = "mes.biz.name-exists";
    /** 并发冲突，请刷新后重试 */
    public static final String BIZ_CONCURRENT_CONFLICT = "mes.biz.concurrent-conflict";
    /** 保存失败 */
    public static final String BIZ_SAVE_FAILED = "mes.biz.save-failed";

    // ---- 单据通用 ----
    /** 单据不存在（{0}=单据编号或 id） */
    public static final String BIZ_DOC_NOT_EXISTS = "mes.biz.doc-not-exists";
    /** 单据已审核，请勿重复审核（{0}=单据编号） */
    public static final String BIZ_DOC_AUDITED = "mes.biz.doc-audited";
    /** 单据未审核，请先审核（{0}=单据编号） */
    public static final String BIZ_DOC_NOT_AUDITED = "mes.biz.doc-not-audited";
    /** 单据已审核过账，不允许删除/修改（{0}=单据编号） */
    public static final String BIZ_DOC_POSTED = "mes.biz.doc-posted";
    /** 单据无明细行，无法审核 */
    public static final String BIZ_DOC_NO_LINES = "mes.biz.doc-no-lines";
    /** 当前状态不允许该操作 */
    public static final String BIZ_STATUS_NOT_ALLOWED = "mes.biz.status-not-allowed";
    /** 期间已结账，不允许改动（{0}=期间） */
    public static final String BIZ_PERIOD_CLOSED = "mes.biz.period-closed";

    // ---- 库存 ----
    /** 库存不足（{0}=仓库，{1}=物料，{2}=数量说明） */
    public static final String BIZ_STOCK_NOT_ENOUGH = "mes.biz.stock-not-enough";
    /** 指定批次库存不足（{0}=仓库，{1}=物料，{2}=批次） */
    public static final String BIZ_BATCH_STOCK_NOT_ENOUGH = "mes.biz.batch-stock-not-enough";
    /** 数量必须大于 0 */
    public static final String BIZ_QTY_POSITIVE = "mes.biz.qty-positive";
    /** 调出仓与调入仓不能相同 */
    public static final String BIZ_SAME_WAREHOUSE = "mes.biz.same-warehouse";
    /** 启用批次管理，必须指定批次号（{0}=物料） */
    public static final String BIZ_BATCH_REQUIRED = "mes.biz.batch-required";
    /** 未启用批次管理，不能指定批次出库（{0}=物料） */
    public static final String BIZ_BATCH_NOT_ALLOWED = "mes.biz.batch-not-allowed";

    // ---- 通用操作 ----
    /** 操作过于频繁，请稍后再试 */
    public static final String BIZ_TOO_FREQUENT = "mes.biz.too-frequent";
    /** 导出失败 */
    public static final String BIZ_EXPORT_FAILED = "mes.biz.export-failed";
    /** 导入失败：文件格式错误 */
    public static final String BIZ_IMPORT_FORMAT_ERROR = "mes.biz.import-format-error";
    /** 已停用，不允许操作 */
    public static final String BIZ_ALREADY_DISABLED = "mes.biz.already-disabled";
}

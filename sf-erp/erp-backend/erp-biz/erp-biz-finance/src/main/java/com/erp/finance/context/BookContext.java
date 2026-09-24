package com.erp.finance.context;

import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;

/**
 * 账套上下文（多账套核心）。
 * <p>
 * 设计说明（为什么不用 MyBatis-Plus TenantLine 插件）：
 * <ul>
 *   <li>{@code MybatisPlusInterceptor} Bean 定义在 erp-framework（共享文件，本模块不可改），
 *       无法在其上追加租户内拦截器；</li>
 *   <li>显式在 Service 层拼 {@code book_id} 条件虽然多几行代码，但行为完全可预测：
 *       不受 XML 手写 SQL / JOIN / 并行模块改动影响，简单可靠；</li>
 *   <li>所有 fin_* 表的写入由各 Service 显式 {@code setBookId}（DB 列带 DEFAULT 1001
 *       仅作为外部模块裸 INSERT 的兜底），查询统一 {@code eq(bookId)}。</li>
 * </ul>
 * 生命周期：{@link BookContextInterceptor} 在 preHandle 解析请求头 X-Book-Id 存入
 * ThreadLocal，afterCompletion 清理；业务代码通过 {@link #getBookId()} 取当前账套。
 */
public final class BookContext {

    /** 默认账套 ID：无 X-Book-Id 请求头时使用（种子主账套） */
    public static final long DEFAULT_BOOK_ID = 1001L;

    /** 账套上下文（请求线程内有效） */
    private static final ThreadLocal<Long> CURRENT_BOOK = new ThreadLocal<>();

    private BookContext() {
    }

    /**
     * 设置当前账套（仅拦截器调用）。
     *
     * @param bookId 账套 ID
     */
    public static void setBookId(Long bookId) {
        CURRENT_BOOK.set(bookId);
    }

    /**
     * 获取当前账套 ID。
     *
     * @return 当前账套 ID；拦截器未覆盖的路径（如定时任务）返回默认账套 1001
     */
    public static Long getBookId() {
        Long bookId = CURRENT_BOOK.get();
        return bookId == null ? DEFAULT_BOOK_ID : bookId;
    }

    /**
     * 清理上下文（拦截器 afterCompletion 调用，防止线程池串号）。
     */
    public static void clear() {
        CURRENT_BOOK.remove();
    }

    /**
     * 数据归属校验：实体必须属于当前账套（横向越权防护）。
     * <p>
     * 例：携带 X-Book-Id:1002 请求 /finance/vouchers/{id}，而该凭证属于 1001，
     * 视同"数据不存在"（不暴露其他账套数据的存在性）。
     *
     * @param entityBookId 实体上的 book_id（可空，空视为旧数据=默认账套）
     * @param what         数据描述（用于异常文案）
     * @throws BusinessException 归属不符时抛 DATA_NOT_EXISTS
     */
    public static void checkBookOwnership(Long entityBookId, String what) {
        long actual = entityBookId == null ? DEFAULT_BOOK_ID : entityBookId;
        if (actual != getBookId()) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.cross-book-forbidden", what, getBookId());
        }
    }
}

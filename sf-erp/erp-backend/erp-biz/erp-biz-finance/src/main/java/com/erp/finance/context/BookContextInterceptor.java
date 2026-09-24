package com.erp.finance.context;

import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.finance.entity.FinBook;
import com.erp.finance.mapper.FinBookMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 账套上下文拦截器。
 * <p>
 * 职责（仅拦截 /finance/** 路径，见 {@code FinanceWebConfig}）：
 * <ol>
 *   <li>preHandle：解析请求头 {@code X-Book-Id}（缺省 1001 主账套），校验账套存在后
 *       写入 {@link BookContext} ThreadLocal；同时把解析结果回显到响应头
 *       {@code X-Book-Id}，前端可据此校对当前生效账套；</li>
 *   <li>afterCompletion：清理 ThreadLocal，防止 Tomcat 线程复用导致账套串号。</li>
 * </ol>
 * 校验规则：非数字或非正数 → 400 类业务异常；数字但账套不存在 → DATA_NOT_EXISTS。
 */
@RequiredArgsConstructor
public class BookContextInterceptor implements HandlerInterceptor {

    /** 账套切换请求头（前端约定）：缺省走默认账套 1001 */
    public static final String HEADER_BOOK_ID = "X-Book-Id";

    private final FinBookMapper bookMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HEADER_BOOK_ID);
        long bookId;
        if (header == null || header.isBlank()) {
            bookId = BookContext.DEFAULT_BOOK_ID;
        } else {
            try {
                bookId = Long.parseLong(header.trim());
            } catch (NumberFormatException e) {
                throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.fin.book-id-invalid", header);
            }
            if (bookId <= 0) {
                throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.fin.book-id-not-positive", header);
            }
        }
        // 账套必须存在（主键点查，代价可忽略）；不存在时给出明确错误而非静默空数据
        FinBook book = bookMapper.selectById(bookId);
        if (book == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.book-not-exists", bookId);
        }
        BookContext.setBookId(bookId);
        // 响应头回显当前生效账套，便于前端联调与排查
        response.setHeader(HEADER_BOOK_ID, String.valueOf(bookId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BookContext.clear();
    }
}

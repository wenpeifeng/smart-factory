package com.erp.finance.config;

import com.erp.finance.context.BookContextInterceptor;
import com.erp.finance.mapper.FinBookMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 财务模块 Web MVC 配置：注册账套上下文拦截器。
 * <p>
 * 仅拦截 {@code /finance/**}（多账套是财务域概念，不影响其他模块）；
 * 账套档案 CRUD（/finance/books）同样被拦截——其本身不受账套隔离，
 * 但请求头合法性校验与响应头回显对它同样生效。
 */
@Configuration
@RequiredArgsConstructor
public class FinanceWebConfig implements WebMvcConfigurer {

    private final FinBookMapper bookMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new BookContextInterceptor(bookMapper))
                .addPathPatterns("/finance/**");
    }
}

package com.erp.it;

import com.erp.basedata.dto.form.BaseCustomerForm;
import com.erp.basedata.dto.form.BaseMaterialForm;
import com.erp.basedata.dto.form.BaseWarehouseForm;
import com.erp.basedata.service.BaseCustomerService;
import com.erp.basedata.service.BaseMaterialService;
import com.erp.basedata.service.BaseWarehouseService;
import org.junit.jupiter.api.AfterAll;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.io.File;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 关键链路集成测试公共基座。
 * <p>
 * 设计要点（与主库 erp_db 完全隔离）：
 * <ul>
 *   <li>MySQL 走 Testcontainers 随机端口容器（镜像与本机 erp-mysql 同为 mysql:8，
 *       无需额外拉取），JDBC 连接串经 @DynamicPropertySource 注入；</li>
 *   <li>Flyway 建表脚本显式指向仓库根 script/sql/migration（按工作目录自适应定位），
 *       容器内从 V1 全量重建，跑完容器销毁，绝不触碰本机 3306 的 erp_db；</li>
 *   <li>Redis 沿用本机 erp-redis（dev profile 默认 localhost:6379）——
 *       集成测试只把它当"单号自增序列"用，key 按天隔离，与业务数据无关；</li>
 *   <li>基础数据（物料/仓库/客户）全部走 basedata Service 造数，保持真实链路；
 *       测试类之间用唯一编码隔离，互不串数据。</li>
 * </ul>
 */
@Tag("it")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ItTestSupport {

    /**
     * i18n 迁移后业务异常消息统一走 messageKey（getMessage() 可能是回退文案），
     * 断言兼容两种形态：直接文案命中，或 messageKey 对应的 zh_CN 资源值命中片段。
     */
    protected static void assertBizMsg(Throwable t, String zhFragment) {
        assertThat(t).as("应抛业务异常").isInstanceOf(com.erp.common.exception.BusinessException.class);
        com.erp.common.exception.BusinessException be = (com.erp.common.exception.BusinessException) t;
        boolean hit = be.getMessage() != null && be.getMessage().contains(zhFragment);
        if (!hit && be.getMessageKey() != null) {
            try (java.io.InputStream in = ItTestSupport.class.getResourceAsStream("/i18n/messages_zh_CN.properties")) {
                if (in != null) {
                    java.util.Properties p = new java.util.Properties();
                    p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    String zh = p.getProperty(be.getMessageKey());
                    hit = zh != null && zh.contains(zhFragment);
                }
            } catch (java.io.IOException ignore) {
                // 资源读取失败按未命中处理
            }
        }
        assertThat(hit).as("异常消息(key=%s,msg=%s)应包含[%s]", be.getMessageKey(), be.getMessage(), zhFragment).isTrue();
    }


    /** 测试库容器（JVM 内所有 IT 类共享一个，静态块只启动一次） */
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8")
            .withDatabaseName("erp_it")
            .withUsername("it")
            .withPassword("it-erp-123456");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // 与 application-dev.yml 同参数风格（utf8 / 东八区 / 免 SSL）
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl()
                        + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                        + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.hikari.connection-timeout", () -> "10000");
        // 建表脚本：仓库根/script/sql/migration（按工作目录自适应，兼容 maven/IDE 两种启动方式）
        registry.add("spring.flyway.locations", () -> "filesystem:" + locateMigrationDir());
    }

    /** 造数编码去重用（测试类内唯一即可） */
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected BaseMaterialService materialService;
    @Autowired
    protected BaseWarehouseService warehouseService;
    @Autowired
    protected BaseCustomerService customerService;

    // ------------------------------------------------------------------
    // 造数 helper（优先走业务 Service，保持真实链路）
    // ------------------------------------------------------------------

    /** 新建启用仓库，返回 ID */
    protected Long newWarehouse(String name) {
        BaseWarehouseForm form = new BaseWarehouseForm();
        form.setName(name);
        form.setStatus(1);
        form.setRemark("IT-集成测试造数");
        return warehouseService.create(form);
    }

    /** 新建启用物料，返回 ID（batchManaged=是否批次管理） */
    protected Long newMaterial(String name, boolean batchManaged) {
        BaseMaterialForm form = new BaseMaterialForm();
        form.setName(name);
        form.setBatchManaged(batchManaged ? 1 : 0);
        form.setStatus(1);
        form.setRemark("IT-集成测试造数");
        return materialService.create(form);
    }

    /** 新建启用客户并设置信用额度，返回 ID（creditLimit=null 表示不控制） */
    protected Long newCustomer(String name, BigDecimal creditLimit) {
        BaseCustomerForm form = new BaseCustomerForm();
        form.setName(name);
        form.setCreditLimit(creditLimit);
        form.setStatus(1);
        form.setRemark("IT-集成测试造数");
        return customerService.create(form);
    }

    /** 唯一后缀（编码/单号去重） */
    protected static String uniqueSuffix() {
        return String.valueOf(SEQ.incrementAndGet());
    }

    /** 模拟登录（销售审核链路需要 SecurityContext 提供 principal=用户ID） */
    protected static void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, "it-test",
                        AuthorityUtils.NO_AUTHORITIES));
    }

    @AfterAll
    static void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // 迁移脚本目录定位
    // ------------------------------------------------------------------

    /**
     * 定位仓库根/script/sql/migration。
     * surefire 默认工作目录是 erp-bootstrap 模块目录（向上两级=仓库根）；
     * IDE 直跑时工作目录可能是仓库根或 erp-backend，逐一探测兜底。
     */
    static String locateMigrationDir() {
        String[] candidates = {"../../script/sql/migration", "script/sql/migration",
                "../script/sql/migration", "../../../script/sql/migration"};
        for (String candidate : candidates) {
            File dir = new File(candidate);
            if (dir.isDirectory() && new File(dir, "V1__system_init.sql").isFile()) {
                return dir.getAbsolutePath();
            }
        }
        throw new IllegalStateException(
                "未定位到 script/sql/migration（工作目录=" + System.getProperty("user.dir") + "）");
    }
}

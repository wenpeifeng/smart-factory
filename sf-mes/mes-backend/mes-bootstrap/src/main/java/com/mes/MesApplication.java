package com.mes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * MES 系统启动类。
 * <p>
 * 组件扫描：默认扫 com.mes.**（所有模块同根包）。
 * Mapper 扫描：com.mes.**.mapper（各业务模块的 mapper 包自动注册）；
 * Bean 名用全限定类名生成 —— 不同模块允许同名 Mapper（如 budget/qc 的
 * CrossModuleMapper），避免默认"简单类名"策略的 bean 名冲突导致启动失败。
 * 全项目均为构造器按类型注入（无 @Resource/@Qualifier 按名注入），改名无影响。
 */
@SpringBootApplication
@MapperScan(basePackages = "com.mes.**.mapper", nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class MesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MesApplication.class, args);
    }
}

package com.sky.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * 架构守则(ArchUnit)：
 * 1. 禁止 @Autowired 字段注入，统一构造器注入(@RequiredArgsConstructor)
 * 2. 依赖单向：controller → service 接口 → mapper
 * 3. 跨模块依赖收敛：menu 为叶子，user → menu，order → user，report → {menu, order, user}
 */
public class ArchitectureTest {

    private static final JavaClasses importedClasses = new ClassFileImporter()
            .importPackages("com.sky");

    @Test
    void 禁止Autowired字段注入() {
        ArchRule rule = noFields()
                .that().areDeclaredInClassesThat(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("com.sky..")
                                .and(com.tngtech.archunit.core.domain.JavaClass.Predicates
                                        .resideOutsideOfPackages("com.sky.test..", "com.sky.architecture..")))
                .should().beAnnotatedWith(Autowired.class)
                .because("统一使用构造器注入(@RequiredArgsConstructor),避免字段注入,便于单元测试与显式依赖");
        rule.check(importedClasses);
    }

    @Test
    void controller不得依赖mapper或service实现() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.sky..controller..")
                .should().dependOnClassesThat().resideInAnyPackage("com.sky..mapper..", "com.sky..service.impl")
                .because("controller 只应依赖 service 接口(依赖倒置),持久层细节由 service 收敛");
        rule.check(importedClasses);
    }

    @Test
    void service实现不得依赖其他web层() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.sky..service.impl")
                .should().dependOnClassesThat().resideInAPackage("com.sky..controller..")
                .because("service 不应反向依赖 controller(单向依赖)");
        rule.check(importedClasses);
    }

    @Test
    void 跨模块依赖收敛menu为叶子() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.sky.menu..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.sky.user..", "com.sky.order..", "com.sky.report..",
                        "com.sky.employee..", "com.sky.shop..")
                .because("menu 为叶子模块,只允许依赖共通模块(common)");
        rule.check(importedClasses);
    }

    @Test
    void 跨模块依赖收敛user依赖方向() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.sky.user..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.sky.order..", "com.sky.report..", "com.sky.employee..", "com.sky.shop..")
                .because("user 只允许依赖 menu(依赖方向 user → menu)");
        rule.check(importedClasses);
    }

    @Test
    void 跨模块依赖收敛order依赖方向() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.sky.order..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.sky.menu..", "com.sky.report..", "com.sky.employee..", "com.sky.shop..")
                .because("order 只允许依赖 user(依赖方向 order → user)");
        rule.check(importedClasses);
    }
}
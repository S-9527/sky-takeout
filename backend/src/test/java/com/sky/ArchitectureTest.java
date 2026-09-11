package com.sky;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * 强制 {@code docs/04-backend.md} §3 的 L1~L7。
 *
 * <p>这些规则不是"最佳实践清单",而是这个仓库对"模块化单体"的具体定义:边界靠测试打红,
 * 而不是靠人记住。规则失败时先判断是代码错了还是意图变了——不要为了让测试变绿而放宽规则。
 *
 * <p>上下文集合是**从字节码里推导**的(见 {@link #contextsOf}),不是写死的清单:
 * 新增一个上下文包会自动进入 L4/L5 的校验范围,不需要回来改测试。
 *
 * <p>只分析生产代码({@code ImportOption.DoNotIncludeTests}):否则测试类自己会被当成
 * "com.sky.&lt;上下文&gt;.domain" 的一部分,AssertJ、JUnit 这些依赖全成了违规。
 */
@AnalyzeClasses(packages = "com.sky", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** 共享内核与装配层:它们不是限界上下文,不参与 L4/L5 的"跨上下文"判定。 */
    private static final Set<String> NON_CONTEXTS = Set.of("common", "security", "config");

    /** L1:api 只依赖同上下文的 service 与 domain,不得直接碰 mapper / gateway。 */
    @ArchTest
    static final ArchRule L1_api_不依赖_mapper_与_gateway =
            noClasses().that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..gateway..");

    /** L2:依赖方向是 api → service,service 不得反向依赖 api。 */
    @ArchTest
    static final ArchRule L2_service_不依赖_api =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..api..");

    /** L3(a):domain 不得依赖 api / service / mapper / gateway 任何一层。 */
    @ArchTest
    static final ArchRule L3_domain_不依赖其它分层 =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..api..", "..service..", "..mapper..", "..gateway..");

    /** L6:禁止字段注入,统一构造器注入(构造器注入让依赖显式且可测)。 */
    @ArchTest
    static final ArchRule L6_禁止字段注入 =
            noFields().that().areDeclaredInClassesThat().resideInAPackage("com.sky..")
                    .should().beAnnotatedWith(Autowired.class);

    /** L7:控制器不得直接使用 mapper。 */
    @ArchTest
    static final ArchRule L7_控制器不碰_mapper =
            noClasses().that().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Controller.class)
                    .should().dependOnClassesThat().resideInAPackage("..mapper..");

    /**
     * L3(b):domain 只能依赖 Java 标准库、Lombok、Spring、MyBatis-Plus、Jackson 注解与共享内核。
     *
     * <p>Jackson **注解**是刻意的例外:实体上的 {@code @JsonIgnore} 是防御性的(万一有人直接
     * 序列化实体,口令哈希也不会外泄),注解只是元数据,不会把领域模型绑到 JSON 实现上。
     * {@code org.apache.ibatis..} 同样是例外:MyBatis-Plus 的 {@code @TableField} 的成员类型
     * ({@code JdbcType} 等)在 MyBatis 包里。
     */
    @ArchTest
    static void L3_domain_不依赖其它框架(JavaClasses classes) {
        String[] domainPackages = contextsOf(classes).stream()
                .map(context -> "com.sky." + context + ".domain..")
                .toArray(String[]::new);

        noClasses().that().resideInAnyPackage(domainPackages)
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        "java..",
                        "lombok..",
                        "org.springframework..",
                        "com.baomidou..",
                        "org.apache.ibatis..",
                        "com.fasterxml.jackson.annotation..",
                        "com.sky.common..",
                        "com.sky.*.domain..")
                .check(classes);
    }

    /**
     * L4:跨上下文调用只能经由对方 {@code service} 包中的类型。
     *
     * <p>直接引用别人的 domain / mapper / api 会让两个上下文在编译期焊死,之后想拆都拆不动。
     */
    @ArchTest
    static void L4_跨上下文只经由对方_service(JavaClasses classes) {
        Set<String> contexts = contextsOf(classes);

        ArchCondition<JavaClass> onlyThroughService = new ArchCondition<>("跨上下文只依赖对方的 service 包") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String origin = contextOf(item.getPackageName(), contexts);
                if (origin == null) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetContext = contextOf(target.getPackageName(), contexts);
                    if (targetContext == null || targetContext.equals(origin)) {
                        continue;
                    }
                    if (!target.getPackageName().equals("com.sky." + targetContext + ".service")) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };

        classes().that().resideInAPackage("com.sky..").should(onlyThroughService).check(classes);
    }

    /** L5:common 与 security 是共享内核,不得依赖任何业务上下文。 */
    @ArchTest
    static void L5_共享内核不依赖业务上下文(JavaClasses classes) {
        String[] contextPackages = contextsOf(classes).stream()
                .map(context -> "com.sky." + context + "..")
                .toArray(String[]::new);

        noClasses().that().resideInAnyPackage("com.sky.common..", "com.sky.security..")
                .should().dependOnClassesThat().resideInAnyPackage(contextPackages)
                .check(classes);
    }

    /** 从字节码推导上下文名:{@code com.sky.<context>.*} 的第二段,排除共享内核与装配层。 */
    private static Set<String> contextsOf(JavaClasses classes) {
        return classes.stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith("com.sky."))
                .map(name -> name.substring("com.sky.".length()))
                .map(remaining -> {
                    int dot = remaining.indexOf('.');
                    return dot < 0 ? remaining : remaining.substring(0, dot);
                })
                .filter(segment -> !NON_CONTEXTS.contains(segment))
                .collect(Collectors.toSet());
    }

    private static String contextOf(String packageName, Set<String> contexts) {
        if (!packageName.startsWith("com.sky.")) {
            return null;
        }
        String remaining = packageName.substring("com.sky.".length());
        int dot = remaining.indexOf('.');
        String segment = dot < 0 ? remaining : remaining.substring(0, dot);
        return contexts.contains(segment) ? segment : null;
    }
}

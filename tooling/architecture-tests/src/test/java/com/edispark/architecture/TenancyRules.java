package com.edispark.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/** Architecture policy only: tenant isolation must also be verified through behavioral contracts. */
final class TenancyRules {
    private static final String TENANT_ID = "com.edispark.identifiers.tenant.TenantId";

    private TenancyRules() { }

    static Set<String> violations(JavaClasses imported, Map<String, String> classifications) {
        var result = classes().should(respectTenantBoundaries(classifications)).evaluate(imported);
        return new TreeSet<>(result.getFailureReport().getDetails());
    }

    private static ArchCondition<JavaClass> respectTenantBoundaries(Map<String, String> classifications) {
        Set<String> globalOwners = classifications.entrySet().stream()
                .filter(entry -> entry.getValue().equals("global"))
                .map(entry -> ownerOf(entry.getKey())).collect(Collectors.toUnmodifiableSet());
        return new ArchCondition<>("keep tenant selection and privileged infrastructure out of business code") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                if (!isBusiness(type)) return;
                type.getDirectDependenciesFromSelf().forEach(dependency -> checkDependency(dependency, globalOwners, events));
                type.getCodeUnitAccessesFromSelf().forEach(call -> {
                    if (selectsTenant(call.getTarget(), classifications)) {
                        reject(call, events, "TENANT_SELECTOR_CALL", call.getOrigin().getFullName()
                                + " -> " + call.getTarget().getFullName());
                    }
                });
                if (isPort(type)) type.getMethods().forEach(method -> checkPort(method, classifications, events));
            }
        };
    }

    private static boolean isBusiness(JavaClass type) {
        String path = "." + type.getPackageName() + ".";
        return path.contains(".application.") || path.contains(".domain.");
    }

    private static boolean isPort(JavaClass type) {
        return type.isInterface() && (type.getSimpleName().endsWith("Repository") || type.getSimpleName().endsWith("Store"));
    }

    private static void checkDependency(Dependency dependency, Set<String> globalOwners, ConditionEvents events) {
        JavaClass target = dependency.getTargetClass();
        String subject = dependency.getDescription().replaceAll(" in \\([^)]*\\)$", "");
        if (isContext(target)) reject(dependency, events, "CONTEXT_ACCESS", subject);
        if (isRawClient(target)) reject(dependency, events, "RAW_STORAGE", subject);
        if (isConcreteInfrastructure(target)) reject(dependency, events, "INFRASTRUCTURE_DEPENDENCY", subject);
        if (globalOwners.contains(target.getName())) reject(dependency, events, "GLOBAL_DISCOVERY_DEPENDENCY", subject);
    }

    private static boolean isContext(JavaClass target) {
        return target.getName().startsWith("io.cottn.core.tenancy.TenantContext")
                || target.getName().equals("io.cottn.core.tenancy.TenantAccess")
                || target.getName().equals("io.cottn.core.tenancy.TenantExecution")
                || target.getName().equals("io.cottn.core.tenancy.TenantTokenHolder");
    }

    private static boolean isRawClient(JavaClass target) {
        return target.getPackageName().startsWith("software.amazon.awssdk.")
                && target.getSimpleName().contains("Client");
    }

    private static boolean isConcreteInfrastructure(JavaClass target) {
        return ("." + target.getPackageName() + ".").contains(".infrastructure.");
    }

    private static void checkPort(JavaMethod method, Map<String, String> classifications, ConditionEvents events) {
        String classification = classifications.get(method.getFullName());
        if ("global".equals(classification)) {
            reject(method, events, "PRIVILEGED_PORT", method.getFullName());
            return;
        }
        if (hasTenantSelector(method, classification)) {
            reject(method, events, "TENANT_SELECTOR", method.getFullName());
            return;
        }
        if (hasUnclassifiedString(method, classification)) {
            reject(method, events, "UNCLASSIFIED_STRING_PORT", method.getFullName());
        }
    }

    private static boolean hasParameter(JavaMethod method, String name) {
        return method.getRawParameterTypes().stream().anyMatch(type -> type.getName().equals(name));
    }

    private static boolean hasTenantSelector(JavaMethod method, String classification) {
        return hasParameter(method, TENANT_ID) || "tenant".equals(classification);
    }

    private static boolean hasUnclassifiedString(JavaMethod method, String classification) {
        return hasParameter(method, String.class.getName()) && !"business".equals(classification);
    }

    private static boolean selectsTenant(CodeUnitAccessTarget target, Map<String, String> classifications) {
        String signature = target.resolveMember().map(JavaCodeUnit::getFullName).orElse(target.getFullName());
        if ("tenant".equals(classifications.get(signature))) return true;
        return isPort(target.getOwner()) && target.getRawParameterTypes().stream()
                .anyMatch(type -> type.getName().equals(TENANT_ID));
    }

    private static String ownerOf(String signature) {
        String qualifiedMethod = signature.substring(0, signature.indexOf('('));
        return qualifiedMethod.substring(0, qualifiedMethod.lastIndexOf('.'));
    }

    private static void reject(Object source, ConditionEvents events, String rule, String subject) {
        events.add(SimpleConditionEvent.violated(source, rule + "\t" + subject));
    }
}

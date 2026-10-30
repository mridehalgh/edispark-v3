package com.edispark.mft.testutil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class AwsClientFake {
    private AwsClientFake() {}

    public static <T> T create(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("close")) return null;
            if (method.getName().equals("serviceName")) return "test";
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            return handler.invoke(proxy, method, args == null ? new Object[0] : args);
        }));
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "AwsClientFake";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }
}

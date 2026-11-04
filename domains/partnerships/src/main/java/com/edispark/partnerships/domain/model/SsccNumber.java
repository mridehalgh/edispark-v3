package com.edispark.partnerships.domain.model;

public final class SsccNumber {
    private SsccNumber() { }

    public static String fromBody(String body) {
        if (body == null || !body.matches("\\d{17}")) {
            throw new IllegalArgumentException("SSCC body must contain 17 digits");
        }
        return body + checkDigit(body);
    }

    public static boolean isValid(String value) {
        return value != null && value.matches("\\d{18}")
                && value.charAt(17) - '0' == checkDigit(value.substring(0, 17));
    }

    private static int checkDigit(String body) {
        int sum = 0;
        for (int index = 0; index < body.length(); index++) {
            sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 3 : 1);
        }
        return (10 - sum % 10) % 10;
    }
}

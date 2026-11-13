package com.edispark.edifact;

import java.util.List;

public record OrderResponseValidation(List<Issue> issues) {
    public OrderResponseValidation { issues = List.copyOf(issues); }
    public boolean valid() { return issues.isEmpty(); }
    public record Issue(String code, String path, String message) {}
    public void requireValid() {
        if (!valid()) throw new InvalidOrderResponse(this);
    }
    public static final class InvalidOrderResponse extends IllegalArgumentException {
        private final OrderResponseValidation report;
        public InvalidOrderResponse(OrderResponseValidation report) {
            super("The Order Response does not meet its target contract.");
            this.report = report;
        }
        public OrderResponseValidation report() { return report; }
    }
}

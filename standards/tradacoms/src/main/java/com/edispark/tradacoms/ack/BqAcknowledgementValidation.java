package com.edispark.tradacoms.ack;

import java.util.List;

public record BqAcknowledgementValidation(List<Issue> issues) {
    public BqAcknowledgementValidation { issues = List.copyOf(issues); }
    public boolean valid() { return issues.isEmpty(); }
    public void requireValid() { if (!valid()) throw new InvalidAcknowledgement(this); }
    public record Issue(String code, String path, String message) { }
    public static final class InvalidAcknowledgement extends IllegalArgumentException {
        private final BqAcknowledgementValidation validation;
        public InvalidAcknowledgement(BqAcknowledgementValidation validation) {
            super("The acknowledgement does not satisfy the B&Q ACK v1.4 profile."); this.validation = validation;
        }
        public BqAcknowledgementValidation validation() { return validation; }
    }
}

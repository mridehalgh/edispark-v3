package com.tradacoms.parser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration options for the TRADACOMS parser.
 */
public final class ParserConfig {

    private final Charset charset;
    private final char segmentTerminator;
    private final boolean lenientMode;
    private final Set<String> messageTypeAllowlist;
    private final Set<String> messageTypeDenylist;
    private final int maxMessages;
    private final boolean stopOnError;
    private final boolean continueOnError;

    private ParserConfig(Builder builder) {
        this.charset = builder.charset;
        this.segmentTerminator = builder.segmentTerminator;
        this.lenientMode = builder.lenientMode;
        this.messageTypeAllowlist = builder.messageTypeAllowlist != null 
                ? Set.copyOf(builder.messageTypeAllowlist) : Set.of();
        this.messageTypeDenylist = builder.messageTypeDenylist != null 
                ? Set.copyOf(builder.messageTypeDenylist) : Set.of();
        this.maxMessages = builder.maxMessages;
        this.stopOnError = builder.stopOnError;
        this.continueOnError = builder.continueOnError;
    }

    public static ParserConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Charset getCharset() {
        return charset;
    }

    public char getSegmentTerminator() {
        return segmentTerminator;
    }

    public boolean isLenientMode() {
        return lenientMode;
    }

    public Set<String> getMessageTypeAllowlist() {
        return messageTypeAllowlist;
    }

    public Set<String> getMessageTypeDenylist() {
        return messageTypeDenylist;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public boolean isStopOnError() {
        return stopOnError;
    }

    public boolean isContinueOnError() {
        return continueOnError;
    }

    /**
     * Checks if a message type should be included based on allowlist/denylist.
     */
    public boolean shouldIncludeMessageType(String messageType) {
        if (!messageTypeAllowlist.isEmpty()) {
            return messageTypeAllowlist.contains(messageType);
        }
        if (!messageTypeDenylist.isEmpty()) {
            return !messageTypeDenylist.contains(messageType);
        }
        return true;
    }

    public static final class Builder {
        private Charset charset = StandardCharsets.ISO_8859_1;
        private char segmentTerminator = TradacomsSyntax.SEGMENT_TERMINATOR;
        private boolean lenientMode = false;
        private Set<String> messageTypeAllowlist = null;
        private Set<String> messageTypeDenylist = null;
        private int maxMessages = Integer.MAX_VALUE;
        private boolean stopOnError = false;
        private boolean continueOnError = false;

        private Builder() {}

        public Builder charset(Charset charset) {
            this.charset = Objects.requireNonNull(charset, "charset must not be null");
            return this;
        }

        public Builder segmentTerminator(char segmentTerminator) {
            this.segmentTerminator = segmentTerminator;
            return this;
        }

        public Builder lenientMode(boolean lenientMode) {
            this.lenientMode = lenientMode;
            return this;
        }

        public Builder messageTypeAllowlist(Set<String> allowlist) {
            this.messageTypeAllowlist = allowlist;
            return this;
        }

        public Builder messageTypeDenylist(Set<String> denylist) {
            this.messageTypeDenylist = denylist;
            return this;
        }

        public Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public Builder stopOnError(boolean stopOnError) {
            this.stopOnError = stopOnError;
            return this;
        }

        public Builder continueOnError(boolean continueOnError) {
            this.continueOnError = continueOnError;
            return this;
        }

        public ParserConfig build() {
            return new ParserConfig(this);
        }
    }
}

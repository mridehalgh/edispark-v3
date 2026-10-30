package com.tradacoms.parser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Configuration options for the TRADACOMS writer.
 */
public final class WriterConfig {

    private final boolean computeControlTotals;
    private final boolean roundTripMode;
    private final boolean deterministicOrdering;
    private final Charset charset;
    private final char segmentTerminator;
    private final char elementSeparator;
    private final char componentSeparator;
    private final char tagSeparator;
    private final char releaseCharacter;

    private WriterConfig(Builder builder) {
        this.computeControlTotals = builder.computeControlTotals;
        this.roundTripMode = builder.roundTripMode;
        this.deterministicOrdering = builder.deterministicOrdering;
        this.charset = builder.charset;
        this.segmentTerminator = builder.segmentTerminator;
        this.elementSeparator = builder.elementSeparator;
        this.componentSeparator = builder.componentSeparator;
        this.tagSeparator = builder.tagSeparator;
        this.releaseCharacter = builder.releaseCharacter;
    }

    public static WriterConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isComputeControlTotals() {
        return computeControlTotals;
    }

    public boolean isRoundTripMode() {
        return roundTripMode;
    }

    public boolean isDeterministicOrdering() {
        return deterministicOrdering;
    }

    public Charset getCharset() {
        return charset;
    }

    public char getSegmentTerminator() {
        return segmentTerminator;
    }

    public char getElementSeparator() {
        return elementSeparator;
    }

    public char getComponentSeparator() {
        return componentSeparator;
    }

    public char getTagSeparator() {
        return tagSeparator;
    }

    public char getReleaseCharacter() {
        return releaseCharacter;
    }

    /**
     * Checks if the given character is a special character that needs escaping.
     */
    public boolean isSpecialCharacter(char c) {
        return c == segmentTerminator ||
                c == elementSeparator ||
                c == componentSeparator ||
                c == tagSeparator ||
                c == releaseCharacter;
    }

    public static final class Builder {
        private boolean computeControlTotals = true;
        private boolean roundTripMode = false;
        private boolean deterministicOrdering = true;
        private Charset charset = StandardCharsets.ISO_8859_1;
        private char segmentTerminator = TradacomsSyntax.SEGMENT_TERMINATOR;
        private char elementSeparator = TradacomsSyntax.ELEMENT_SEPARATOR;
        private char componentSeparator = TradacomsSyntax.COMPONENT_SEPARATOR;
        private char tagSeparator = TradacomsSyntax.TAG_SEPARATOR;
        private char releaseCharacter = TradacomsSyntax.RELEASE_CHARACTER;

        private Builder() {}

        public Builder computeControlTotals(boolean computeControlTotals) {
            this.computeControlTotals = computeControlTotals;
            return this;
        }

        public Builder roundTripMode(boolean roundTripMode) {
            this.roundTripMode = roundTripMode;
            return this;
        }

        public Builder deterministicOrdering(boolean deterministicOrdering) {
            this.deterministicOrdering = deterministicOrdering;
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = Objects.requireNonNull(charset, "charset must not be null");
            return this;
        }

        public Builder segmentTerminator(char segmentTerminator) {
            this.segmentTerminator = segmentTerminator;
            return this;
        }

        public Builder elementSeparator(char elementSeparator) {
            this.elementSeparator = elementSeparator;
            return this;
        }

        public Builder componentSeparator(char componentSeparator) {
            this.componentSeparator = componentSeparator;
            return this;
        }

        public Builder tagSeparator(char tagSeparator) {
            this.tagSeparator = tagSeparator;
            return this;
        }

        public Builder releaseCharacter(char releaseCharacter) {
            this.releaseCharacter = releaseCharacter;
            return this;
        }

        public WriterConfig build() {
            return new WriterConfig(this);
        }
    }
}

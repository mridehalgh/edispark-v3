package com.tradacoms.parser.lexer;

import com.tradacoms.parser.TradacomsSyntax;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Configuration options for the TRADACOMS lexer.
 */
public final class LexerConfig {

    private final Charset charset;
    private final char segmentTerminator;
    private final char elementSeparator;
    private final char componentSeparator;
    private final char tagSeparator;
    private final char releaseCharacter;

    private LexerConfig(Builder builder) {
        this.charset = builder.charset;
        this.segmentTerminator = builder.segmentTerminator;
        this.elementSeparator = builder.elementSeparator;
        this.componentSeparator = builder.componentSeparator;
        this.tagSeparator = builder.tagSeparator;
        this.releaseCharacter = builder.releaseCharacter;
    }

    /**
     * Returns the default configuration using standard TRADACOMS syntax.
     */
    public static LexerConfig defaults() {
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
        private Charset charset = StandardCharsets.ISO_8859_1;
        private char segmentTerminator = TradacomsSyntax.SEGMENT_TERMINATOR;
        private char elementSeparator = TradacomsSyntax.ELEMENT_SEPARATOR;
        private char componentSeparator = TradacomsSyntax.COMPONENT_SEPARATOR;
        private char tagSeparator = TradacomsSyntax.TAG_SEPARATOR;
        private char releaseCharacter = TradacomsSyntax.RELEASE_CHARACTER;

        private Builder() {}

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

        public LexerConfig build() {
            return new LexerConfig(this);
        }
    }
}

package com.tradacoms.parser.lexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Lexer for tokenizing TRADACOMS EDI input.
 * 
 * Converts raw TRADACOMS text into a stream of tokens representing
 * segment starts, element values, component values, segment ends, and errors.
 * 
 * Handles release character escaping for special characters.
 */
public final class Lexer {

    private final LexerConfig config;

    public Lexer() {
        this(LexerConfig.defaults());
    }

    public Lexer(LexerConfig config) {
        this.config = config != null ? config : LexerConfig.defaults();
    }

    /**
     * Tokenizes TRADACOMS input from an InputStream.
     */
    public Iterator<Token> tokenize(InputStream input) {
        Reader reader = new BufferedReader(new InputStreamReader(input, config.getCharset()));
        return new TokenIterator(reader, config);
    }

    /**
     * Tokenizes TRADACOMS input from a String.
     */
    public Iterator<Token> tokenize(String input) {
        Reader reader = new StringReader(input);
        return new TokenIterator(reader, config);
    }

    /**
     * Tokenizes TRADACOMS input from a Reader.
     */
    public Iterator<Token> tokenize(Reader reader) {
        return new TokenIterator(reader, config);
    }

    /**
     * Escapes special characters in a string using the release character.
     */
    public String escape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 10);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (config.isSpecialCharacter(c)) {
                sb.append(config.getReleaseCharacter());
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Unescapes a string by removing release characters before special characters.
     */
    public String unescape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        char release = config.getReleaseCharacter();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == release && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (config.isSpecialCharacter(next)) {
                    // Skip the release character, add the escaped char
                    sb.append(next);
                    i += 2; // Skip both the release char and the escaped char
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * Returns the configuration used by this lexer.
     */
    public LexerConfig getConfig() {
        return config;
    }

    /**
     * Internal iterator that produces tokens from the input.
     */
    private static final class TokenIterator implements Iterator<Token> {

        private final Reader reader;
        private final LexerConfig config;
        private final StringBuilder buffer;
        private final StringBuilder snippetBuffer;
        private final Deque<Token> tokenQueue;

        private int currentLine = 1;
        private int currentPosition = 0;
        private int segmentStartLine = 1;
        private int segmentStartPosition = 0;

        private int elementIndex = 0;
        private int componentIndex = 0;

        private boolean finished = false;
        private boolean inSegment = false;
        private boolean tagParsed = false;

        TokenIterator(Reader reader, LexerConfig config) {
            this.reader = reader;
            this.config = config;
            this.buffer = new StringBuilder(256);
            this.snippetBuffer = new StringBuilder(100);
            this.tokenQueue = new ArrayDeque<>();
        }

        @Override
        public boolean hasNext() {
            if (!tokenQueue.isEmpty()) {
                return true;
            }
            if (finished) {
                return false;
            }
            fillQueue();
            return !tokenQueue.isEmpty();
        }

        @Override
        public Token next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more tokens");
            }
            return tokenQueue.poll();
        }

        private void fillQueue() {
            if (finished) {
                return;
            }
            try {
                readTokens();
            } catch (IOException e) {
                finished = true;
                tokenQueue.add(new Token.Error("I/O error: " + e.getMessage(), 
                        currentLine, currentPosition, snippetBuffer.toString()));
            }
        }

        private void readTokens() throws IOException {
            buffer.setLength(0);
            boolean escaped = false;

            while (tokenQueue.isEmpty() && !finished) {
                int ch = reader.read();

                if (ch == -1) {
                    handleEndOfInput();
                    return;
                }

                char c = (char) ch;
                updateSnippetBuffer(c);

                // Handle release character escaping
                if (c == config.getReleaseCharacter() && !escaped) {
                    escaped = true;
                    buffer.append(c);
                    updatePosition(c);
                    continue;
                }

                if (escaped) {
                    // The character after release is literal
                    escaped = false;
                    buffer.append(c);
                    updatePosition(c);
                    continue;
                }

                // Skip whitespace between segments (newlines, carriage returns)
                if (!inSegment && (c == '\n' || c == '\r' || c == ' ' || c == '\t')) {
                    updatePosition(c);
                    continue;
                }

                // Check for special characters
                if (c == config.getSegmentTerminator()) {
                    handleSegmentTerminator();
                    updatePosition(c);
                    continue;
                }

                if (c == config.getTagSeparator() && !inSegment) {
                    handleTagSeparator();
                    updatePosition(c);
                    continue;
                }

                if (c == config.getElementSeparator() && inSegment) {
                    handleElementSeparator();
                    updatePosition(c);
                    continue;
                }

                if (c == config.getComponentSeparator() && inSegment && tagParsed) {
                    handleComponentSeparator();
                    updatePosition(c);
                    continue;
                }

                // Regular character - add to buffer
                if (!inSegment && buffer.isEmpty()) {
                    // Starting a new segment - record position
                    segmentStartLine = currentLine;
                    segmentStartPosition = currentPosition;
                }
                buffer.append(c);
                updatePosition(c);
            }
        }

        private void handleEndOfInput() {
            if (inSegment) {
                // Unterminated segment
                tokenQueue.add(new Token.Error("Unexpected end of input - unterminated segment", 
                        currentLine, currentPosition, snippetBuffer.toString()));
            }
            finished = true;
            tokenQueue.add(new Token.EndOfInput());
        }

        private void handleTagSeparator() {
            String tag = buffer.toString().trim();
            buffer.setLength(0);

            if (tag.isEmpty()) {
                tokenQueue.add(new Token.Error("Empty segment tag", currentLine, currentPosition, 
                        snippetBuffer.toString()));
                return;
            }

            inSegment = true;
            tagParsed = true;
            elementIndex = 0;
            componentIndex = 0;

            tokenQueue.add(new Token.SegmentStart(tag, segmentStartLine, segmentStartPosition));
        }

        private void handleElementSeparator() {
            String value = buffer.toString();
            buffer.setLength(0);

            if (componentIndex > 0) {
                // We were in a composite element, emit the last component
                tokenQueue.add(new Token.ComponentValue(value, componentIndex));
            } else {
                // Simple element value
                tokenQueue.add(new Token.ElementValue(value, elementIndex));
            }

            elementIndex++;
            componentIndex = 0;
        }

        private void handleComponentSeparator() {
            String value = buffer.toString();
            buffer.setLength(0);

            tokenQueue.add(new Token.ComponentValue(value, componentIndex));
            componentIndex++;
        }

        private void handleSegmentTerminator() {
            if (!inSegment && !buffer.isEmpty()) {
                // Segment without tag separator - treat buffer as tag with no elements
                String tag = buffer.toString().trim();
                buffer.setLength(0);
                tokenQueue.add(new Token.SegmentStart(tag, segmentStartLine, segmentStartPosition));
                tokenQueue.add(new Token.SegmentEnd(currentLine, currentPosition));
                return;
            }

            if (inSegment) {
                // Emit any remaining content
                String value = buffer.toString();
                buffer.setLength(0);

                // Always emit the last element/component if we've started parsing elements
                // (elementIndex > 0 means we've seen at least one element separator)
                // or if there's actual content
                if (!value.isEmpty() || componentIndex > 0 || elementIndex > 0) {
                    if (componentIndex > 0) {
                        // Last component of composite element
                        tokenQueue.add(new Token.ComponentValue(value, componentIndex));
                    } else {
                        // Last element value (could be empty if we just saw a + before ')
                        tokenQueue.add(new Token.ElementValue(value, elementIndex));
                    }
                }

                tokenQueue.add(new Token.SegmentEnd(currentLine, currentPosition));
                resetSegmentState();
            }
            // If not in segment and buffer is empty, just skip (empty segment)
        }

        private void resetSegmentState() {
            inSegment = false;
            tagParsed = false;
            elementIndex = 0;
            componentIndex = 0;
        }

        private void updatePosition(char c) {
            if (c == '\n') {
                currentLine++;
                currentPosition = 0;
            } else {
                currentPosition++;
            }
        }

        private void updateSnippetBuffer(char c) {
            if (snippetBuffer.length() >= 100) {
                snippetBuffer.delete(0, 50);
            }
            snippetBuffer.append(c);
        }
    }
}

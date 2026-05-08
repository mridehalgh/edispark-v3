package com.tradacoms.parser.lexer;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for special character escaping in TRADACOMS.
 * 
 * **Feature: tradacoms-parser, Property 10: Special Character Escaping**
 * **Validates: Requirements 8.5**
 */
class EscapingPropertyTest {

    private final Lexer lexer = new Lexer();

    /**
     * Property 10: Special Character Escaping
     * 
     * *For any* string containing TRADACOMS special characters (', +, :, =, ?),
     * serializing and then parsing SHALL recover the original string value.
     * The release character (?) SHALL correctly escape special characters.
     */
    @Property(tries = 100)
    void escapeUnescapeRoundTrip(
            @ForAll("stringsWithSpecialChars") String original
    ) {
        // Escape the string
        String escaped = lexer.escape(original);
        
        // Unescape should recover original
        String recovered = lexer.unescape(escaped);
        
        assertEquals(original, recovered, 
                "Round-trip failed for: '" + original + "' -> '" + escaped + "' -> '" + recovered + "'");
    }

    /**
     * Property: Escaped strings should not contain unescaped special characters
     * (except the release character which escapes them).
     */
    @Property(tries = 100)
    void escapedStringsHaveNoUnescapedSpecialChars(
            @ForAll("stringsWithSpecialChars") String original
    ) {
        String escaped = lexer.escape(original);
        LexerConfig config = lexer.getConfig();
        
        // Check that all special characters in the escaped string are preceded by release char
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (isSpecialChar(c, config) && c != config.getReleaseCharacter()) {
                // This special char must be preceded by release character
                assertTrue(i > 0 && escaped.charAt(i - 1) == config.getReleaseCharacter(),
                        "Unescaped special character '" + c + "' at position " + i + 
                        " in escaped string: " + escaped);
            }
        }
    }

    /**
     * Property: Escaping should be idempotent in terms of round-trip behavior.
     * escape(escape(x)) when unescaped twice should give back x.
     */
    @Property(tries = 100)
    void doubleEscapeDoubleUnescape(
            @ForAll("stringsWithSpecialChars") String original
    ) {
        String escaped1 = lexer.escape(original);
        String escaped2 = lexer.escape(escaped1);
        
        String unescaped1 = lexer.unescape(escaped2);
        String unescaped2 = lexer.unescape(unescaped1);
        
        assertEquals(original, unescaped2,
                "Double escape/unescape failed for: " + original);
    }

    /**
     * Property: Strings without special characters should remain unchanged after escaping.
     */
    @Property(tries = 100)
    void stringsWithoutSpecialCharsUnchanged(
            @ForAll("safeStrings") String original
    ) {
        String escaped = lexer.escape(original);
        assertEquals(original, escaped,
                "String without special chars should not change: " + original);
    }

    /**
     * Property: Tokenizing an escaped value in a segment should recover the original.
     * This tests the full round-trip through the lexer.
     */
    @Property(tries = 100)
    void lexerRoundTripWithEscapedValues(
            @ForAll("nonEmptyStringsWithSpecialChars") String original
    ) {
        // Escape the value and put it in a segment
        String escaped = lexer.escape(original);
        String segment = "TEST=" + escaped + "'";
        
        // Tokenize
        Iterator<Token> tokens = lexer.tokenize(segment);
        List<Token> tokenList = new ArrayList<>();
        while (tokens.hasNext()) {
            Token t = tokens.next();
            if (t instanceof Token.EndOfInput) break;
            tokenList.add(t);
        }
        
        // Find the element value
        Optional<String> elementValue = tokenList.stream()
                .filter(t -> t instanceof Token.ElementValue)
                .map(t -> ((Token.ElementValue) t).value())
                .findFirst();
        
        assertTrue(elementValue.isPresent(), "Should have an element value for: " + original);
        
        // The lexer returns the raw escaped value, so we need to unescape it
        String recovered = lexer.unescape(elementValue.get());
        assertEquals(original, recovered,
                "Lexer round-trip failed for: '" + original + "'");
    }

    /**
     * Property: Each special character when escaped should double in representation.
     */
    @Property(tries = 100)
    void singleSpecialCharEscaping(
            @ForAll("singleSpecialChars") char specialChar
    ) {
        String original = String.valueOf(specialChar);
        String escaped = lexer.escape(original);
        
        // Should be release char followed by the special char
        assertEquals(2, escaped.length(),
                "Escaped special char should be 2 chars: " + escaped);
        assertEquals(lexer.getConfig().getReleaseCharacter(), escaped.charAt(0),
                "First char should be release character");
        assertEquals(specialChar, escaped.charAt(1),
                "Second char should be the special character");
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> stringsWithSpecialChars() {
        // Generate strings that may contain TRADACOMS special characters
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 '+:=?")
                .ofMinLength(0)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> nonEmptyStringsWithSpecialChars() {
        // Generate non-empty strings that may contain TRADACOMS special characters
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 '+:=?")
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> safeStrings() {
        // Generate strings without any special characters
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ")
                .ofMinLength(0)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<Character> singleSpecialChars() {
        return Arbitraries.of('\'', '+', ':', '=', '?');
    }

    private boolean isSpecialChar(char c, LexerConfig config) {
        return c == config.getSegmentTerminator() ||
                c == config.getElementSeparator() ||
                c == config.getComponentSeparator() ||
                c == config.getTagSeparator() ||
                c == config.getReleaseCharacter();
    }
}

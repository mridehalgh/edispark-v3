package com.tradacoms.parser.lexer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the TRADACOMS Lexer.
 */
class LexerTest {

    private final Lexer lexer = new Lexer();

    @Test
    void testSimpleSegment() {
        // STX=ANAA:1+5000000000000:SENDER+5000000000001:RECEIVER'
        // Tokens: SegmentStart(STX), ComponentValue(ANAA,0), ComponentValue(1,1), 
        //         ComponentValue(5000000000000,0), ComponentValue(SENDER,1),
        //         ComponentValue(5000000000001,0), ComponentValue(RECEIVER,1), SegmentEnd
        String input = "STX=ANAA:1+5000000000000:SENDER+5000000000001:RECEIVER'";
        List<Token> tokens = collectTokens(input);

        assertEquals(8, tokens.size());
        assertInstanceOf(Token.SegmentStart.class, tokens.get(0));
        assertEquals("STX", ((Token.SegmentStart) tokens.get(0)).tag());

        // First element is composite: ANAA:1
        assertInstanceOf(Token.ComponentValue.class, tokens.get(1));
        assertEquals("ANAA", ((Token.ComponentValue) tokens.get(1)).value());
        assertInstanceOf(Token.ComponentValue.class, tokens.get(2));
        assertEquals("1", ((Token.ComponentValue) tokens.get(2)).value());

        // Second element is composite: 5000000000000:SENDER
        assertInstanceOf(Token.ComponentValue.class, tokens.get(3));
        assertEquals("5000000000000", ((Token.ComponentValue) tokens.get(3)).value());
        assertInstanceOf(Token.ComponentValue.class, tokens.get(4));
        assertEquals("SENDER", ((Token.ComponentValue) tokens.get(4)).value());

        // Third element is composite: 5000000000001:RECEIVER
        assertInstanceOf(Token.ComponentValue.class, tokens.get(5));
        assertEquals("5000000000001", ((Token.ComponentValue) tokens.get(5)).value());
        assertInstanceOf(Token.ComponentValue.class, tokens.get(6));
        assertEquals("RECEIVER", ((Token.ComponentValue) tokens.get(6)).value());

        assertInstanceOf(Token.SegmentEnd.class, tokens.get(7));
    }

    @Test
    void testMultipleSegments() {
        String input = "MHD=1+ORDERS:9'CLO=123456'MTR=2'";
        List<Token> tokens = collectTokens(input);

        // Count segment starts
        long segmentStarts = tokens.stream()
                .filter(t -> t instanceof Token.SegmentStart)
                .count();
        assertEquals(3, segmentStarts);

        // Verify tags
        List<String> tags = tokens.stream()
                .filter(t -> t instanceof Token.SegmentStart)
                .map(t -> ((Token.SegmentStart) t).tag())
                .toList();
        assertEquals(List.of("MHD", "CLO", "MTR"), tags);
    }

    @Test
    void testElementValues() {
        String input = "OLD=1+PRODUCT+100+EA'";
        List<Token> tokens = collectTokens(input);

        // Should have: SegmentStart, ElementValue(1), ElementValue(PRODUCT), 
        // ElementValue(100), ElementValue(EA), SegmentEnd
        List<String> elementValues = tokens.stream()
                .filter(t -> t instanceof Token.ElementValue)
                .map(t -> ((Token.ElementValue) t).value())
                .toList();
        assertEquals(List.of("1", "PRODUCT", "100", "EA"), elementValues);
    }

    @Test
    void testCompositeElements() {
        String input = "TEST=A:B:C+D'";
        List<Token> tokens = collectTokens(input);

        // First element is composite A:B:C
        List<String> componentValues = tokens.stream()
                .filter(t -> t instanceof Token.ComponentValue)
                .map(t -> ((Token.ComponentValue) t).value())
                .toList();
        assertEquals(List.of("A", "B", "C"), componentValues);

        // Second element is simple D
        List<String> elementValues = tokens.stream()
                .filter(t -> t instanceof Token.ElementValue)
                .map(t -> ((Token.ElementValue) t).value())
                .toList();
        assertEquals(List.of("D"), elementValues);
    }

    @Test
    void testEscapedCharacters() {
        // Test escaping the segment terminator with ?
        String input = "TEST=Value with ?'quote'";
        List<Token> tokens = collectTokens(input);

        List<String> values = tokens.stream()
                .filter(t -> t instanceof Token.ElementValue)
                .map(t -> ((Token.ElementValue) t).value())
                .toList();
        assertEquals(1, values.size());
        assertEquals("Value with ?'quote", values.get(0));
    }

    @Test
    void testEscapedPlusSign() {
        String input = "TEST=A?+B'";
        List<Token> tokens = collectTokens(input);

        List<String> values = tokens.stream()
                .filter(t -> t instanceof Token.ElementValue)
                .map(t -> ((Token.ElementValue) t).value())
                .toList();
        assertEquals(1, values.size());
        assertEquals("A?+B", values.get(0));
    }

    @Test
    void testEscapedColon() {
        String input = "TEST=A?:B'";
        List<Token> tokens = collectTokens(input);

        // Should be single element, not composite
        List<String> elementValues = tokens.stream()
                .filter(t -> t instanceof Token.ElementValue)
                .map(t -> ((Token.ElementValue) t).value())
                .toList();
        assertEquals(1, elementValues.size());
        assertEquals("A?:B", elementValues.get(0));
    }

    @Test
    void testEscapedReleaseCharacter() {
        String input = "TEST=A??B'";
        List<Token> tokens = collectTokens(input);

        List<String> values = tokens.stream()
                .filter(t -> t instanceof Token.ElementValue)
                .map(t -> ((Token.ElementValue) t).value())
                .toList();
        assertEquals(1, values.size());
        assertEquals("A??B", values.get(0));
    }

    @Test
    void testEmptyElements() {
        // TEST=++VALUE+'
        // Elements: "", "", "VALUE", ""
        // But the trailing empty element after the last + before ' needs special handling
        String input = "TEST=++VALUE+'";
        List<Token> tokens = collectTokens(input);

        List<String> values = tokens.stream()
                .filter(t -> t instanceof Token.ElementValue)
                .map(t -> ((Token.ElementValue) t).value())
                .toList();
        // The trailing empty element is emitted when we hit the segment terminator
        assertEquals(List.of("", "", "VALUE", ""), values);
    }

    @Test
    void testLineNumberTracking() {
        String input = "SEG1=A'\nSEG2=B'\n\nSEG3=C'";
        List<Token> tokens = collectTokens(input);

        List<Token.SegmentStart> starts = tokens.stream()
                .filter(t -> t instanceof Token.SegmentStart)
                .map(t -> (Token.SegmentStart) t)
                .toList();

        assertEquals(3, starts.size());
        assertEquals(1, starts.get(0).line());
        assertEquals(2, starts.get(1).line());
        assertEquals(4, starts.get(2).line());
    }

    @Test
    void testEndOfInput() {
        String input = "TEST=A'";
        Iterator<Token> iter = lexer.tokenize(input);

        List<Token> tokens = new ArrayList<>();
        while (iter.hasNext()) {
            tokens.add(iter.next());
        }

        assertTrue(tokens.get(tokens.size() - 1) instanceof Token.EndOfInput);
    }

    @Test
    void testEscapeMethod() {
        assertEquals("A?+B", lexer.escape("A+B"));
        assertEquals("A?:B", lexer.escape("A:B"));
        assertEquals("A?'B", lexer.escape("A'B"));
        assertEquals("A?=B", lexer.escape("A=B"));
        assertEquals("A??B", lexer.escape("A?B"));
        assertEquals("A?+?:?'?=??B", lexer.escape("A+:\'=?B"));
    }

    @Test
    void testUnescapeMethod() {
        assertEquals("A+B", lexer.unescape("A?+B"));
        assertEquals("A:B", lexer.unescape("A?:B"));
        assertEquals("A'B", lexer.unescape("A?'B"));
        assertEquals("A=B", lexer.unescape("A?=B"));
        assertEquals("A?B", lexer.unescape("A??B"));
    }

    @Test
    void testEscapeUnescapeRoundTrip() {
        String[] testStrings = {
            "Simple text",
            "Text with + plus",
            "Text with : colon",
            "Text with ' quote",
            "Text with = equals",
            "Text with ? question",
            "All special +:'=? chars",
            ""
        };

        for (String original : testStrings) {
            String escaped = lexer.escape(original);
            String unescaped = lexer.unescape(escaped);
            assertEquals(original, unescaped, "Round-trip failed for: " + original);
        }
    }

    @Test
    void testWhitespaceBetweenSegments() {
        String input = "SEG1=A'  \n  \t  SEG2=B'";
        List<Token> tokens = collectTokens(input);

        List<String> tags = tokens.stream()
                .filter(t -> t instanceof Token.SegmentStart)
                .map(t -> ((Token.SegmentStart) t).tag())
                .toList();
        assertEquals(List.of("SEG1", "SEG2"), tags);
    }

    private List<Token> collectTokens(String input) {
        Iterator<Token> iter = lexer.tokenize(input);
        List<Token> tokens = new ArrayList<>();
        while (iter.hasNext()) {
            Token token = iter.next();
            if (token instanceof Token.EndOfInput) {
                break;
            }
            tokens.add(token);
        }
        return tokens;
    }
}

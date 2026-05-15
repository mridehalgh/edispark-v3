package com.example.documents.tradacoms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradacoms.parser.EdiParser;
import com.tradacoms.parser.ParseException;

@DisplayName("TradacomsInboundFixtures")
class TradacomsInboundFixturesTest {

    private final EdiParser parser = new EdiParser();

    @Test
    @DisplayName("uses CREDIT as the first supported inbound message shape")
    void usesCreditAsFirstSupportedInboundMessageShape() {
        var batch = parser.parseBatch(new String(TradacomsInboundFixtures.validSupportedPayload()));

        assertThat(batch.messageCount()).isEqualTo(1);
        assertThat(batch.getMessages().getFirst().getMessageType())
                .isEqualTo(TradacomsInboundFixtures.SUPPORTED_MESSAGE_TYPE);
    }

    @Test
    @DisplayName("keeps an unsupported but structurally valid ORDERS payload fixture")
    void keepsUnsupportedButStructurallyValidOrdersFixture() {
        var batch = parser.parseBatch(new String(TradacomsInboundFixtures.unsupportedSourcePayload()));

        assertThat(batch.messageCount()).isEqualTo(1);
        assertThat(batch.getMessages().getFirst().getMessageType())
                .isEqualTo(TradacomsInboundFixtures.UNSUPPORTED_MESSAGE_TYPE);
    }

    @Test
    @DisplayName("keeps an invalid fixture that fails parser validation")
    void keepsInvalidFixtureThatFailsParserValidation() {
        assertThatThrownBy(() -> parser.parseBatch(new String(TradacomsInboundFixtures.invalidSourcePayload())))
                .isInstanceOf(ParseException.class);
    }
}

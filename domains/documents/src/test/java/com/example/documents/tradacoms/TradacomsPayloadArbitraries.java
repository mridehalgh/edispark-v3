package com.example.documents.tradacoms;

import java.nio.charset.StandardCharsets;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

public final class TradacomsPayloadArbitraries {

    private TradacomsPayloadArbitraries() {
    }

    public static Arbitrary<SupportedTradacomsPayload> supportedTradacomsPayloads() {
        return Combinators.combine(interchanges(), creditReferences(), creditLineGroups())
                .as((interchange, creditReference, creditLines) -> {
                    String payload = renderSupportedPayload(interchange, creditReference, creditLines);
                    return new SupportedTradacomsPayload(
                            payload.getBytes(StandardCharsets.UTF_8),
                            TradacomsInboundFixtures.SUPPORTED_MESSAGE_TYPE);
                });
    }

    public static Arbitrary<FailingTradacomsPayload> failingTradacomsPayloads() {
        return Combinators.combine(supportedTradacomsPayloads(), Arbitraries.of(FailureMutation.values()))
                .as((payload, mutation) -> mutation.mutate(payload));
    }

    private static Arbitrary<TradacomsInterchange> interchanges() {
        return Combinators.combine(partnerIds(), partnerSuffixes(), partnerIds(), partnerSuffixes(), tradacomsDates(),
                tradacomsTimes(), transmissionReferences())
                .as(TradacomsInterchange::new);
    }

    private static Arbitrary<CreditReference> creditReferences() {
        return Combinators.combine(buyerIds(), buyerNames(), creditNumbers(), tradacomsDates(), tradacomsDates())
                .as(CreditReference::new);
    }

    private static Arbitrary<List<CreditLine>> creditLineGroups() {
        return creditLineSeeds().list().ofMinSize(1).ofMaxSize(5)
                .map(seeds -> {
                    java.util.ArrayList<CreditLine> lines = new java.util.ArrayList<>();
                    for (int index = 0; index < seeds.size(); index++) {
                        CreditLineSeed seed = seeds.get(index);
                        lines.add(new CreditLine(
                                String.valueOf(index + 1),
                                seed.sku(),
                                seed.quantity(),
                                seed.unitPrice(),
                                seed.quantity() * seed.unitPrice(),
                                seed.allowanceCode()));
                    }
                    return List.copyOf(lines);
                });
    }

    private static Arbitrary<CreditLineSeed> creditLineSeeds() {
        return Combinators.combine(stockKeepingUnits(), Arbitraries.integers().between(1, 50),
                Arbitraries.integers().between(100, 9999), Arbitraries.of("", "01"))
                .as(CreditLineSeed::new);
    }

    private static Arbitrary<String> partnerIds() {
        return digits(13);
    }

    private static Arbitrary<String> partnerSuffixes() {
        return Arbitraries.of("SENDER", "RECEIVER", "OPS", "A");
    }

    private static Arbitrary<String> buyerIds() {
        return uppercaseAlphaNumeric(3, 12);
    }

    private static Arbitrary<String> buyerNames() {
        return Arbitraries.of("", "Buyer Name", "Accounts Payable", "Northern Branch");
    }

    private static Arbitrary<String> creditNumbers() {
        return uppercaseAlphaNumeric(4, 10).map(value -> "CN" + value);
    }

    private static Arbitrary<String> transmissionReferences() {
        return uppercaseAlphaNumeric(4, 12).map(value -> "TR" + value);
    }

    private static Arbitrary<String> stockKeepingUnits() {
        return uppercaseAlphaNumeric(3, 10).map(value -> "SKU" + value);
    }

    private static Arbitrary<String> tradacomsDates() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 28),
                Arbitraries.integers().between(1, 12),
                Arbitraries.integers().between(24, 29))
                .as((day, month, year) -> "%02d%02d%02d".formatted(day, month, year));
    }

    private static Arbitrary<String> tradacomsTimes() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 23),
                Arbitraries.integers().between(0, 59),
                Arbitraries.integers().between(0, 59))
                .as((hour, minute, second) -> "%02d%02d%02d".formatted(hour, minute, second));
    }

    private static Arbitrary<String> digits(int length) {
        return Arbitraries.strings()
                .withChars("0123456789")
                .ofLength(length);
    }

    private static Arbitrary<String> uppercaseAlphaNumeric(int minLength, int maxLength) {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(minLength)
                .ofMaxLength(maxLength);
    }

    private static String renderSupportedPayload(
            TradacomsInterchange interchange,
            CreditReference creditReference,
            List<CreditLine> creditLines) {
        StringBuilder payload = new StringBuilder()
                .append("STX=ANAA:1+")
                .append(interchange.senderId())
                .append(":")
                .append(interchange.senderSuffix())
                .append("+")
                .append(interchange.receiverId())
                .append(":")
                .append(interchange.receiverSuffix())
                .append("+")
                .append(interchange.date())
                .append(":")
                .append(interchange.time())
                .append("+")
                .append(interchange.transmissionReference())
                .append("'")
                .append("MHD=1+CREDIT:9'")
                .append("CLO=")
                .append(creditReference.buyerId())
                .append("+")
                .append(creditReference.buyerName())
                .append("'")
                .append("CRF=")
                .append(creditReference.documentNumber())
                .append("+")
                .append(creditReference.documentDate())
                .append("+")
                .append(creditReference.taxPointDate())
                .append("'");

        for (CreditLine creditLine : creditLines) {
            payload.append("CLD=")
                    .append(creditLine.lineNumber())
                    .append("+::")
                    .append(creditLine.sku())
                    .append("++++")
                    .append(creditLine.quantity())
                    .append("+")
                    .append(creditLine.unitPrice())
                    .append("+")
                    .append(creditLine.lineValue())
                    .append("+S+17.5+")
                    .append(creditLine.allowanceCode())
                    .append("'");
        }

        return payload.append("MTR=")
                .append(3 + creditLines.size())
                .append("'")
                .append("END=1'")
                .toString();
    }

    private static String renderUnsupportedOrdersPayload(SupportedTradacomsPayload payload) {
        String validPayload = new String(payload.bytes(), StandardCharsets.UTF_8);
        String unsupportedHeader = validPayload.replace("MHD=1+CREDIT:9'", "MHD=1+ORDERS:9'");
        return unsupportedHeader.replaceFirst("CRF=[^']*'", "OLD=1+SKU123'");
    }

    public record SupportedTradacomsPayload(byte[] bytes, String messageType) {
    }

    public record FailingTradacomsPayload(byte[] bytes, com.example.documents.application.tradacoms.ParseStatus expectedStatus,
            String expectedMessageType) {
    }

    private record TradacomsInterchange(
            String senderId,
            String senderSuffix,
            String receiverId,
            String receiverSuffix,
            String date,
            String time,
            String transmissionReference) {
    }

    private record CreditReference(
            String buyerId,
            String buyerName,
            String documentNumber,
            String documentDate,
            String taxPointDate) {
    }

    private record CreditLine(String lineNumber, String sku, int quantity, int unitPrice, int lineValue,
            String allowanceCode) {
    }

    private record CreditLineSeed(String sku, int quantity, int unitPrice, String allowanceCode) {
    }

    private enum FailureMutation {
        MISSING_MTR {
            @Override
            FailingTradacomsPayload mutate(SupportedTradacomsPayload payload) {
                String raw = new String(payload.bytes(), StandardCharsets.UTF_8);
                String mutated = raw.replaceFirst("MTR=[^']*'", "");
                return new FailingTradacomsPayload(
                        mutated.getBytes(StandardCharsets.UTF_8),
                        com.example.documents.application.tradacoms.ParseStatus.INVALID_SYNTAX,
                        payload.messageType());
            }
        },
        WRONG_MTR_COUNT {
            @Override
            FailingTradacomsPayload mutate(SupportedTradacomsPayload payload) {
                String raw = new String(payload.bytes(), StandardCharsets.UTF_8);
                String mutated = raw.replaceFirst("MTR=\\d+'", "MTR=0'");
                return new FailingTradacomsPayload(
                        mutated.getBytes(StandardCharsets.UTF_8),
                        com.example.documents.application.tradacoms.ParseStatus.INVALID_SYNTAX,
                        payload.messageType());
            }
        },
        MISSING_CLO {
            @Override
            FailingTradacomsPayload mutate(SupportedTradacomsPayload payload) {
                String raw = new String(payload.bytes(), StandardCharsets.UTF_8);
                String mutated = raw.replaceFirst("CLO=[^']*'", "");
                return new FailingTradacomsPayload(
                        mutated.getBytes(StandardCharsets.UTF_8),
                        com.example.documents.application.tradacoms.ParseStatus.INVALID_SYNTAX,
                        payload.messageType());
            }
        },
        UNSUPPORTED_ORDERS {
            @Override
            FailingTradacomsPayload mutate(SupportedTradacomsPayload payload) {
                String mutated = renderUnsupportedOrdersPayload(payload);
                return new FailingTradacomsPayload(
                        mutated.getBytes(StandardCharsets.UTF_8),
                        com.example.documents.application.tradacoms.ParseStatus.UNSUPPORTED_MESSAGE,
                        TradacomsInboundFixtures.UNSUPPORTED_MESSAGE_TYPE);
            }
        };

        abstract FailingTradacomsPayload mutate(SupportedTradacomsPayload payload);
    }
}

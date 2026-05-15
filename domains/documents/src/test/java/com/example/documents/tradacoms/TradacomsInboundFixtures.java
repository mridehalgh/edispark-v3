package com.example.documents.tradacoms;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Reusable inbound TRADACOMS fixtures for the first CREDIT slice.
 */
public final class TradacomsInboundFixtures {

    public static final String SUPPORTED_MESSAGE_TYPE = "CREDIT";
    public static final String UNSUPPORTED_MESSAGE_TYPE = "ORDERS";

    private TradacomsInboundFixtures() {
    }

    public static byte[] validSupportedPayload() {
        return "STX=ANAA:1+5012345678901:SENDER+5098765432109:RECEIVER+260115:143052+TRANS001'"
                .concat("MHD=1+CREDIT:9'")
                .concat("CLO=BUYER001+Buyer Name'")
                .concat("CRF=CN001+260115+260115'")
                .concat("CLD=1+::SKU123++++10+1000+1000+S+17.5+01'")
                .concat("MTR=4'")
                .concat("END=1'")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] invalidSourcePayload() {
        return "STX=ANAA:1+5012345678901:SENDER+5098765432109:RECEIVER+260115:143052+TRANS002'"
                .concat("MHD=1+CREDIT:9'")
                .concat("CLO=BUYER001+Buyer Name'")
                .concat("CRF=CN002+260115+260115'")
                .concat("CLD=1+::SKU123++++10+1000+1000+S+17.5+01'")
                .concat("END=1'")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] unsupportedSourcePayload() {
        return "STX=ANAA:1+5012345678901:SENDER+5098765432109:RECEIVER+260115:143052+TRANS003'"
                .concat("MHD=1+ORDERS:9'")
                .concat("CLO=BUYER001+Buyer Name'")
                .concat("OLD=1+SKU123'")
                .concat("ODD=First line'")
                .concat("MTR=4'")
                .concat("END=1'")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static String sourceFileName() {
        return "credit-note.edi";
    }

    public static Map<String, String> interchangeMetadata() {
        return Map.of(
                "senderId", "5012345678901",
                "receiverId", "5098765432109",
                "batchId", "TRANS001");
    }
}

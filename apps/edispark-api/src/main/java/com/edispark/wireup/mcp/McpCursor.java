package com.edispark.wireup.mcp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

record McpCursor(String secret) {
    static final int TOKEN_LENGTH = 64;
    static final int PAGE_TOKEN_LENGTH = 4096;

    String wrapPage(String position, String tenant, String filter) {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        return base64.encodeToString(position.getBytes(StandardCharsets.UTF_8)) + "."
                + base64.encodeToString(signature(position, tenant, filter));
    }

    String unwrapPage(String token, String tenant, String filter) {
        if (token.length() > PAGE_TOKEN_LENGTH) throw new IllegalArgumentException();
        var parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isEmpty()) throw new IllegalArgumentException();
        var decoder = Base64.getUrlDecoder();
        var position = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(decoder.decode(parts[1]), signature(position, tenant, filter))) {
            throw new IllegalArgumentException();
        }
        return position;
    }

    String encode(UUID id, String tenant, String filter) {
        byte[] value = ByteBuffer.allocate(48).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .put(signature(id.toString(), tenant, filter)).array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    UUID decode(String token, String tenant, String filter) {
        if (token.length() != TOKEN_LENGTH) throw new IllegalArgumentException();
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        if (decoded.length != 48) throw new IllegalArgumentException();
        var value = ByteBuffer.wrap(decoded);
        UUID id = new UUID(value.getLong(), value.getLong());
        byte[] supplied = new byte[32];
        value.get(supplied);
        if (!MessageDigest.isEqual(supplied, signature(id.toString(), tenant, filter))) throw new IllegalArgumentException();
        return id;
    }

    private byte[] signature(String value, String tenant, String filter) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(("edispark-mcp-cursor\n" + tenant + "\n" + value + "\n" + filter)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

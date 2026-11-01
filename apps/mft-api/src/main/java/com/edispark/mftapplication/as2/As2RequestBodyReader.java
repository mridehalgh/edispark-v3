package com.edispark.mftapplication.as2;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class As2RequestBodyReader {
    private static final int BUFFER_SIZE = 16 * 1024;
    private final int maximumBytes;

    As2RequestBodyReader(long maximumBytes) {
        if (maximumBytes < 1 || maximumBytes > Integer.MAX_VALUE - 1) {
            throw new IllegalArgumentException("AS2 maximum message size is invalid");
        }
        this.maximumBytes = (int) maximumBytes;
    }

    byte[] read(HttpServletRequest request) throws IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maximumBytes) throw new As2MessageTooLargeException();
        return read(request.getInputStream());
    }

    byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, BUFFER_SIZE));
        byte[] buffer = new byte[BUFFER_SIZE];
        int total = 0;
        for (int read; (read = input.read(buffer)) != -1; ) {
            if (read == 0) continue;
            total += read;
            if (total > maximumBytes) throw new As2MessageTooLargeException();
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}

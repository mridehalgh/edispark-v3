package com.edispark.mftapplication.as2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class As2RequestBodyReaderTest {
    @Test
    void readsExactlyTheConfiguredMaximum() throws Exception {
        byte[] body = new byte[]{1, 2, 3, 4};
        assertThat(new As2RequestBodyReader(4).read(new ByteArrayInputStream(body))).isEqualTo(body);
    }

    @Test
    void rejectsChunkedBodiesThatExceedTheConfiguredMaximum() {
        assertThatThrownBy(() -> new As2RequestBodyReader(3)
                .read(new ByteArrayInputStream(new byte[]{1, 2, 3, 4})))
                .isInstanceOf(As2MessageTooLargeException.class);
    }
}

package com.edispark.mft.infrastructure.as2;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.mft.application.As2TransportException;
import java.net.URI;
import org.junit.jupiter.api.Test;

class As2EndpointPolicyTest {
    private final As2EndpointPolicy policy = new As2EndpointPolicy(true);

    @Test
    void asynchronousMdnCannotBeRedirectedToAnotherOrigin() {
        assertThatThrownBy(() -> policy.requireSameOrigin(
                URI.create("https://retailer.example/as2"),
                URI.create("https://attacker.example/capture")))
                .isInstanceOf(As2TransportException.class)
                .hasMessageContaining("partner endpoint origin");
    }

    @Test
    void endpointCredentialsAndFragmentsAreRejected() {
        assertThatThrownBy(() -> policy.validate(URI.create("https://user@retailer.example/as2")))
                .isInstanceOf(As2TransportException.class);
        assertThatThrownBy(() -> policy.validate(URI.create("https://retailer.example/as2#fragment")))
                .isInstanceOf(As2TransportException.class);
    }
}

package com.edispark.mft.infrastructure.as2;

import com.edispark.mft.application.As2TransportException;
import java.net.InetAddress;
import java.net.URI;

public final class As2EndpointPolicy {
    private final boolean allowPrivateNetworks;

    public As2EndpointPolicy(boolean allowPrivateNetworks) {
        this.allowPrivateNetworks = allowPrivateNetworks;
    }

    public void validate(URI endpoint) {
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new As2TransportException("AS2 endpoint must use HTTP or HTTPS", null, false);
        }
        if (endpoint.getUserInfo() != null || endpoint.getHost() == null || endpoint.getFragment() != null) {
            throw new As2TransportException("AS2 endpoint is invalid", null, false);
        }
        if (allowPrivateNetworks) return;
        try {
            for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
                if (restricted(address)) {
                    throw new As2TransportException("AS2 endpoint resolves to a restricted network", null, false);
                }
            }
        } catch (As2TransportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new As2TransportException("AS2 endpoint could not be resolved", exception, true);
        }
    }

    public void requireSameOrigin(URI configuredEndpoint, URI callback) {
        validate(callback);
        if (!configuredEndpoint.getScheme().equalsIgnoreCase(callback.getScheme())
                || !configuredEndpoint.getHost().equalsIgnoreCase(callback.getHost())
                || effectivePort(configuredEndpoint) != effectivePort(callback)) {
            throw new As2TransportException("Asynchronous MDN URL must use the partner endpoint origin", null, false);
        }
    }

    private boolean restricted(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress();
    }

    private int effectivePort(URI value) {
        if (value.getPort() >= 0) return value.getPort();
        return "https".equalsIgnoreCase(value.getScheme()) ? 443 : 80;
    }
}

package com.finpay.gateway.upstream;

/** Thrown when an upstream service cannot be reached; mapped to 503. */
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
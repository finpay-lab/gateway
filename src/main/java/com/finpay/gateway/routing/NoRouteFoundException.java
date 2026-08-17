package com.finpay.gateway.routing;

/** No route matches the request path; mapped to 404. */
public class NoRouteFoundException extends RuntimeException {

    public NoRouteFoundException(String path) {
        super("No route configured for path: " + path);
    }
}
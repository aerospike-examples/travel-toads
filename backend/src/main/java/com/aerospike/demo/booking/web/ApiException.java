package com.aerospike.demo.booking.web;

/** A deliberate HTTP error response with a JSON body, as opposed to an unexpected 500. */
public final class ApiException extends RuntimeException {
    public final int status;
    public final String error;

    public ApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    public static ApiException conflict(String error, String message) {
        return new ApiException(409, error, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }
}

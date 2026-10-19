package com.example.fitapp.common;

import org.springframework.http.HttpStatus;

/** Stable machine-readable error codes returned in the "code" property of every ProblemDetail. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INFEASIBLE_PLAN(HttpStatus.UNPROCESSABLE_ENTITY),
    CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

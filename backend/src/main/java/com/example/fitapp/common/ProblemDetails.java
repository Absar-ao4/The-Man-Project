package com.example.fitapp.common;

import java.net.URI;
import java.util.Map;
import org.springframework.http.ProblemDetail;

/** Builds RFC 7807 bodies with the stable "code" property. */
public final class ProblemDetails {

    private ProblemDetails() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        return of(code, detail, Map.of());
    }

    public static ProblemDetail of(ErrorCode code, String detail, Map<String, String> fieldErrors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setTitle(code.name());
        pd.setType(URI.create("urn:problem:" + code.name().toLowerCase().replace('_', '-')));
        pd.setProperty("code", code.name());
        if (!fieldErrors.isEmpty()) {
            pd.setProperty("errors", fieldErrors);
        }
        return pd;
    }
}

package com.example.fitapp.auth;

import com.example.fitapp.common.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The only way services learn who is calling: the id comes from the JWT, never a request. */
public final class CurrentUser {

    private CurrentUser() {}

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw ApiException.unauthorized("Authentication required");
    }

    public static UUID id() {
        return get().id();
    }
}

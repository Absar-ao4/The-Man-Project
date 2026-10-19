package com.example.fitapp.auth;

import java.util.UUID;

/** The authenticated principal; the user id always comes from the verified JWT. */
public record AuthUser(UUID id, Role role) {}

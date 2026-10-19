package com.example.fitapp.auth;

import com.example.fitapp.auth.dto.AuthResponse;
import com.example.fitapp.auth.dto.LoginRequest;
import com.example.fitapp.auth.dto.RegisterRequest;
import com.example.fitapp.auth.dto.UserDto;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.JwtProperties;
import com.example.fitapp.profile.ProfileService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties props;
    private final ProfileService profiles;
    /** Checked when the email is unknown, so timing does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder,
            JwtService jwt,
            JwtProperties props,
            ProfileService profiles) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.profiles = profiles;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        String username = req.username().trim();
        if (users.existsByEmail(email)) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "Email is already registered",
                    Map.of("email", "is already registered"));
        }
        if (users.existsByUsername(username)) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "Username is already taken",
                    Map.of("username", "is already taken"));
        }
        User user =
                users.saveAndFlush(
                        new User(UUID.randomUUID(), email, username, encoder.encode(req.password())));
        profiles.createInitial(user.getId(), req.name().trim());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = encoder.matches(req.password(), hash);
        if (user == null || !matches) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return issueTokens(user);
    }

    /**
     * Rotates the refresh token. Presenting an already-revoked token signals theft or replay, so
     * every session of that user is revoked. The exception must not roll that revocation back.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        Instant now = Instant.now();
        RefreshToken stored =
                refreshTokens
                        .findByTokenHash(hash(rawToken))
                        .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (stored.isRevoked()) {
            refreshTokens.revokeAllForUser(stored.getUserId(), now);
            throw ApiException.unauthorized("Refresh token was already used");
        }
        if (stored.isExpired(now)) {
            throw ApiException.unauthorized("Refresh token expired");
        }
        User user =
                users.findById(stored.getUserId())
                        .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        stored.revoke(now);
        refreshTokens.save(stored);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens
                .findByTokenHash(hash(rawToken))
                .ifPresent(
                        t -> {
                            t.revoke(Instant.now());
                            refreshTokens.save(t);
                        });
    }

    private AuthResponse issueTokens(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = Instant.now().plus(props.refreshTokenTtl());
        refreshTokens.save(new RefreshToken(UUID.randomUUID(), user.getId(), hash(raw), expires));
        return new AuthResponse(
                jwt.issueAccessToken(user),
                raw,
                jwt.accessTokenTtlSeconds(),
                new UserDto(
                        user.getId(),
                        user.getEmail(),
                        user.getUsername(),
                        user.getPlan(),
                        user.getRole()));
    }

    static String hash(String raw) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

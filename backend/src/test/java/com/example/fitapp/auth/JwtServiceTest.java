package com.example.fitapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fitapp.config.JwtProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private static JwtService service(String secret, Duration ttl) {
        return new JwtService(new JwtProperties(secret, ttl, Duration.ofDays(30)));
    }

    private static User user(Role role) {
        User u = new User(UUID.randomUUID(), "a@example.com", "alice", "hash");
        u.setRole(role);
        return u;
    }

    @Test
    void roundTripsIdentityAndRole() {
        JwtService jwt = service(SECRET, Duration.ofMinutes(15));
        User u = user(Role.USER);
        AuthUser parsed = jwt.parse(jwt.issueAccessToken(u)).orElseThrow();
        assertThat(parsed.id()).isEqualTo(u.getId());
        assertThat(parsed.role()).isEqualTo(Role.USER);
    }

    @Test
    void carriesAdminRole() {
        JwtService jwt = service(SECRET, Duration.ofMinutes(15));
        AuthUser parsed = jwt.parse(jwt.issueAccessToken(user(Role.ADMIN))).orElseThrow();
        assertThat(parsed.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = service(SECRET, Duration.ofSeconds(-5));
        assertThat(jwt.parse(jwt.issueAccessToken(user(Role.USER)))).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String token =
                service("another-secret-another-secret-0123456789", Duration.ofMinutes(15))
                        .issueAccessToken(user(Role.USER));
        assertThat(service(SECRET, Duration.ofMinutes(15)).parse(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        JwtService jwt = service(SECRET, Duration.ofMinutes(15));
        assertThat(jwt.parse("not.a.jwt")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();
    }

    @Test
    void refusesToStartWithShortSecret() {
        assertThatThrownBy(() -> service("too-short", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
    }
}

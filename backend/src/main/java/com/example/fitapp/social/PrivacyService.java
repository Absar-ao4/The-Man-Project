package com.example.fitapp.social;

import com.example.fitapp.social.SocialDtos.PrivacyDto;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** What friends may see. Without a row the defaults apply: feed on, leaderboard on, PR weights hidden. */
@Service
public class PrivacyService {

    public static final PrivacyDto DEFAULTS = new PrivacyDto(true, false, true);

    private final JdbcTemplate jdbc;

    public PrivacyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PrivacyDto get(UUID userId) {
        return jdbc.query(
                        "select show_feed, show_pr_weights, show_on_leaderboard from privacy_settings where user_id = ?",
                        (rs, i) -> new PrivacyDto(rs.getBoolean("show_feed"), rs.getBoolean("show_pr_weights"), rs.getBoolean("show_on_leaderboard")),
                        userId)
                .stream()
                .findFirst()
                .orElse(DEFAULTS);
    }

    public PrivacyDto update(UUID userId, PrivacyDto settings) {
        jdbc.update(
                "insert into privacy_settings (user_id, show_feed, show_pr_weights, show_on_leaderboard, updated_at) values (?, ?, ?, ?, now())"
                        + " on conflict (user_id) do update set show_feed = excluded.show_feed, show_pr_weights = excluded.show_pr_weights,"
                        + " show_on_leaderboard = excluded.show_on_leaderboard, updated_at = now()",
                userId,
                settings.showFeed(),
                settings.showPrWeights(),
                settings.showOnLeaderboard());
        return get(userId);
    }

    /** The settings of several users at once (users without a row get the defaults). */
    public Map<UUID, PrivacyDto> getAll(Collection<UUID> userIds) {
        Map<UUID, PrivacyDto> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        String marks = String.join(",", userIds.stream().map(i -> "?").toList());
        jdbc.query(
                "select user_id, show_feed, show_pr_weights, show_on_leaderboard from privacy_settings where user_id in (" + marks + ")",
                rs -> {
                    result.put(rs.getObject("user_id", UUID.class), new PrivacyDto(rs.getBoolean("show_feed"), rs.getBoolean("show_pr_weights"), rs.getBoolean("show_on_leaderboard")));
                },
                userIds.toArray());
        userIds.forEach(id -> result.putIfAbsent(id, DEFAULTS));
        return result;
    }
}

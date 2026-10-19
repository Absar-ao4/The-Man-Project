package com.example.fitapp.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fitapp.account.AccountService.Source;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountServiceTest {

    @Test
    void everySectionHasAUniqueKey() {
        Set<String> keys = new HashSet<>();
        for (Source s : AccountService.SOURCES) {
            assertThat(keys.add(s.key())).as("key " + s.key()).isTrue();
        }
    }

    @Test
    void everyQueryHasAsManyPlacesForTheUserAsItSays() {
        for (Source s : AccountService.SOURCES) {
            long marks = s.sql().chars().filter(c -> c == '?').count();
            assertThat(marks).as(s.key()).isEqualTo(s.userParams());
        }
    }

    @Test
    void theExportNeverSelectsASecret() {
        for (Source s : AccountService.SOURCES) {
            assertThat(s.sql().toLowerCase()).as(s.key()).doesNotContain("password_hash").doesNotContain("refresh_tokens");
        }
        assertThat(AccountService.SOURCES.stream().filter(s -> s.key().equals("account")).findFirst().orElseThrow().sql()).doesNotContain("*");
    }

    @Test
    void everyQueryIsLimitedToTheCallersOwnRows() {
        for (Source s : AccountService.SOURCES) {
            assertThat(s.sql().toLowerCase()).as(s.key()).contains("= ?");
        }
    }
}

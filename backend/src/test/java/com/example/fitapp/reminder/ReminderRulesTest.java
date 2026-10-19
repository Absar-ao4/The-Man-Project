package com.example.fitapp.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fitapp.reminder.ReminderRules.Checked;
import com.example.fitapp.reminder.ReminderRules.Invalid;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderRulesTest {

    @Test
    void aGoodChoiceComesBackTidy() {
        Checked c = ReminderRules.check(ReminderType.WORKOUT, "18:30", List.of(5, 1, 3, 1), null, "  Go   lift  ");

        assertThat(c.time()).isEqualTo(LocalTime.of(18, 30));
        assertThat(c.days()).as("sorted, no repeats").containsExactly(1, 3, 5);
        assertThat(c.intervalMinutes()).isNull();
        assertThat(c.message()).as("spaces tidied").isEqualTo("Go lift");
    }

    @Test
    void secondsAreDroppedFromTheTime() {
        assertThat(ReminderRules.check(ReminderType.WEIGH_IN, "07:30:45", List.of(1), null, null).time()).isEqualTo(LocalTime.of(7, 30));
    }

    @Test
    void badTimesAreRefused() {
        for (String bad : new String[] {null, "", "  ", "25:00", "7:3x", "noon", "24:00"}) {
            assertThatThrownBy(() -> ReminderRules.check(ReminderType.WORKOUT, bad, List.of(1), null, null)).as("time " + bad).isInstanceOf(Invalid.class).extracting("field").isEqualTo("time");
        }
    }

    @Test
    void daysMustBeOneToSevenAndNotEmpty() {
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WORKOUT, "08:00", List.of(), null, null)).isInstanceOf(Invalid.class);
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WORKOUT, "08:00", null, null, null)).isInstanceOf(Invalid.class);
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WORKOUT, "08:00", List.of(0), null, null)).isInstanceOf(Invalid.class);
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WORKOUT, "08:00", List.of(8), null, null)).isInstanceOf(Invalid.class);
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WORKOUT, "08:00", Arrays.asList(1, null), null, null)).isInstanceOf(Invalid.class);
        assertThat(ReminderRules.check(ReminderType.WORKOUT, "08:00", List.of(1, 2, 3, 4, 5, 6, 7), null, null).days()).hasSize(7);
    }

    @Test
    void onlyTheWaterReminderRepeatsAndItDefaultsToTwoHours() {
        assertThat(ReminderRules.check(ReminderType.WATER, "09:00", List.of(1), null, null).intervalMinutes()).isEqualTo(120);
        assertThat(ReminderRules.check(ReminderType.WATER, "09:00", List.of(1), 45, null).intervalMinutes()).isEqualTo(45);
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WATER, "09:00", List.of(1), 29, null)).isInstanceOf(Invalid.class);
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WATER, "09:00", List.of(1), 721, null)).isInstanceOf(Invalid.class);
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WORKOUT, "09:00", List.of(1), 60, null)).isInstanceOf(Invalid.class).extracting("field").isEqualTo("intervalMinutes");
    }

    @Test
    void messagesArePlainTextAndShort() {
        assertThat(ReminderRules.check(ReminderType.WORKOUT, "09:00", List.of(1), null, "   ").message()).isNull();
        assertThat(ReminderRules.check(ReminderType.WORKOUT, "09:00", List.of(1), null, "Line one\nline two\u0007").message()).isEqualTo("Line one line two");
        assertThat(ReminderRules.check(ReminderType.WORKOUT, "09:00", List.of(1), null, "x".repeat(140)).message()).hasSize(140);
        assertThatThrownBy(() -> ReminderRules.check(ReminderType.WORKOUT, "09:00", List.of(1), null, "x".repeat(141))).isInstanceOf(Invalid.class).extracting("field").isEqualTo("customMessage");
    }
}

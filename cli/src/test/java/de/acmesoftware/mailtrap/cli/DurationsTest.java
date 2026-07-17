package de.acmesoftware.mailtrap.cli;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationsTest {

    @Test
    void parsesSuffixes() {
        assertThat(Durations.parse("30s")).isEqualTo(Duration.ofSeconds(30));
        assertThat(Durations.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(Durations.parse("2m")).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void bareNumberIsSeconds() {
        assertThat(Durations.parse("10")).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void zeroIsAllowed() {
        assertThat(Durations.parse("0").isZero()).isTrue();
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> Durations.parse("soon"))
                .isInstanceOf(CliError.class)
                .hasMessageContaining("Cannot parse duration");
    }
}

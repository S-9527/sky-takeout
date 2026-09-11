package com.sky.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private static final JwtProperties PROPERTIES = new JwtProperties(
            "sky-takeout", "secret", Duration.ofHours(2), Duration.ofDays(7), Duration.ofHours(1), Duration.ofDays(3));

    @Test
    void accessTtlDependsOnAudience() {
        assertThat(PROPERTIES.accessTtl(Audience.ADMIN)).isEqualTo(Duration.ofHours(2));
        assertThat(PROPERTIES.accessTtl(Audience.CUSTOMER)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void refreshTtlDependsOnAudience() {
        assertThat(PROPERTIES.refreshTtl(Audience.ADMIN)).isEqualTo(Duration.ofDays(7));
        assertThat(PROPERTIES.refreshTtl(Audience.CUSTOMER)).isEqualTo(Duration.ofDays(3));
    }
}

package com.threatdetection.gateway.auth.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void acceptsEightCharacterPasswordWithoutComplexityRequirement() {
        assertThat(PasswordPolicy.isValid("12345678")).isTrue();
        assertThat(PasswordPolicy.isValid("abcdefgh")).isTrue();
    }

    @Test
    void rejectsSevenCharacterPassword() {
        assertThat(PasswordPolicy.isValid("Abc1234")).isFalse();
    }

    @Test
    void rejectsSeventeenCharacterPassword() {
        assertThat(PasswordPolicy.isValid("12345678901234567")).isFalse();
    }
}

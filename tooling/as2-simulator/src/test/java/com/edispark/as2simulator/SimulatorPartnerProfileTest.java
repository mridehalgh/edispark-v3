package com.edispark.as2simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SimulatorPartnerProfileTest {
    private final SimulatorController controller = new SimulatorController(new SimulatorStore(), "identity.p12",
            "password", "SIMULATOR", "simulator", "SIMULATOR_TRADACOMS", "EDISPARK", "receiver.cer",
            "http://localhost:8080/as2");

    @Test
    void selectsTheAs2IdentityThatMatchesTheSeededEdiPartner() {
        assertThat(controller.senderFor("EDIFACT")).isEqualTo("SIMULATOR");
        assertThat(controller.senderFor("tradacoms")).isEqualTo("SIMULATOR_TRADACOMS");
    }

    @Test
    void rejectsUnknownProfilesWithTheAllowedCorrection() {
        assertThatThrownBy(() -> controller.senderFor("x12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown simulator profile. Choose EDIFACT or TRADACOMS.");
    }
}

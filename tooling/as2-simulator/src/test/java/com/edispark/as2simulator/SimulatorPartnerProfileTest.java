package com.edispark.as2simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SimulatorPartnerProfileTest {
    @Test void mapsBothLogicalReceiverProfilesToTheConfiguredPrivateKeyAlias() throws Exception {
        var factory = new SimulatorPartnershipFactory("SIMULATOR", "SIMULATOR_TRADACOMS", "simulator", "EDISPARK");
        for (String id : java.util.List.of("SIMULATOR", "SIMULATOR_TRADACOMS")) {
            var partnership = new com.helger.phase2.partner.Partnership(id);
            partnership.setSenderAS2ID("EDISPARK"); partnership.setReceiverAS2ID(id);
            factory.onBeforeAddPartnership(partnership);
            assertThat(partnership.getReceiverAS2ID()).isEqualTo(id);
            assertThat(partnership.getReceiverX509Alias()).isEqualTo("simulator");
            var mdn = new com.helger.phase2.partner.Partnership("MDN-" + id);
            mdn.setSenderAS2ID(id); mdn.setReceiverAS2ID("EDISPARK");
            factory.onBeforeAddPartnership(mdn);
            assertThat(mdn.getSenderX509Alias()).isEqualTo("simulator");
            assertThat(mdn.getReceiverX509Alias()).isEqualTo("EDISPARK");
        }
        var unknown = new com.helger.phase2.partner.Partnership("unknown");
        unknown.setReceiverAS2ID("OTHER");
        assertThatThrownBy(() -> factory.onBeforeAddPartnership(unknown)).hasMessageContaining("Unknown local");
    }
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

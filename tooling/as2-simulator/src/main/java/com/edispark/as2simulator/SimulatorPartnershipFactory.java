package com.edispark.as2simulator;

import com.helger.phase2.partner.Partnership;
import com.helger.phase2.partner.SelfFillingPartnershipFactory;
import com.helger.phase2.exception.AS2Exception;
import java.util.Set;

/** Local logical trading profiles use the same disposable simulator identity. */
final class SimulatorPartnershipFactory extends SelfFillingPartnershipFactory {
    private final Set<String> receiverIds;
    private final String alias;
    private final String edisparkId;
    SimulatorPartnershipFactory(String edifactId, String tradacomsId, String alias, String edisparkId) {
        this.receiverIds = Set.of(edifactId, tradacomsId, "SIMULATOR_TESCO"); this.alias = alias; this.edisparkId = edisparkId;
    }
    @Override protected void onBeforeAddPartnership(Partnership partnership) throws AS2Exception {
        super.onBeforeAddPartnership(partnership);
        if (isSimulator(partnership.getReceiverAS2ID()) && edisparkId.equals(partnership.getSenderAS2ID())) {
            partnership.setReceiverX509Alias(alias);
        } else if (isSimulator(partnership.getSenderAS2ID()) && edisparkId.equals(partnership.getReceiverAS2ID())) {
            partnership.setSenderX509Alias(alias); // Synchronous MDN reverses sender and receiver.
        } else throw new AS2Exception("Unknown local simulator receiver identity");
    }
    private boolean isSimulator(String id) { return id != null && receiverIds.contains(id); }
}

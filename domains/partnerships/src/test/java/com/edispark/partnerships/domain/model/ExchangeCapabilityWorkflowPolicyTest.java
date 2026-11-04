package com.edispark.partnerships.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeCapabilityWorkflowPolicyTest {

    @Test
    void existingCapabilitiesDefaultToManualWorkflow() {
        ExchangeCapability capability = legacyCapability(ExchangeDirection.INBOUND);

        assertThat(capability.automationMode()).isEqualTo(WorkflowAutomationMode.MANUAL);
        assertThat(capability.approvalPolicy()).isEqualTo(WorkflowApprovalPolicy.NONE);
    }

    @Test
    void approvalRequiredModeNeedsAnApproverPolicy() {
        assertThatThrownBy(() -> capability(
                ExchangeDirection.INBOUND,
                WorkflowAutomationMode.APPROVAL_REQUIRED,
                WorkflowApprovalPolicy.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approval policy");
    }

    @Test
    void outboundRoutesCannotTriggerResponseAutomation() {
        assertThatThrownBy(() -> capability(
                ExchangeDirection.OUTBOUND,
                WorkflowAutomationMode.AUTO_DRAFT,
                WorkflowApprovalPolicy.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inbound capabilities");
    }

    private ExchangeCapability legacyCapability(ExchangeDirection direction) {
        return new ExchangeCapability(UUID.randomUUID(), direction, BusinessDocumentType.ORDER,
                EdiStandard.EDIFACT, "D96A", "ORDERS", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY,
                BatchingPolicy.INDIVIDUAL);
    }

    private ExchangeCapability capability(ExchangeDirection direction, WorkflowAutomationMode mode,
            WorkflowApprovalPolicy approval) {
        return new ExchangeCapability(UUID.randomUUID(), direction, BusinessDocumentType.ORDER,
                EdiStandard.EDIFACT, "D96A", "ORDERS", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY,
                BatchingPolicy.INDIVIDUAL, OutboundLossPolicy.STRICT, mode, approval,
                BusinessDocumentType.ORDER_RESPONSE);
    }
}

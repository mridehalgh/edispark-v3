package com.edispark.partnerships.domain.model;

/** How an inbound business event progresses through its configured response workflow. */
public enum WorkflowAutomationMode {
    MANUAL,
    AUTO_DRAFT,
    APPROVAL_REQUIRED,
    AUTO_SEND
}

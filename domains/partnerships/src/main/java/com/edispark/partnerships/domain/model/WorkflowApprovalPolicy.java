package com.edispark.partnerships.domain.model;

/** Human approval required before a workflow may finalize or send a document. */
public enum WorkflowApprovalPolicy {
    NONE,
    AUTHORIZED_MEMBER,
    FOUR_EYES
}

package com.edispark.documents.domain.model;

/**
 * A category of business document that defines its semantic purpose.
 */
public enum DocumentType {
    INVOICE,
    ORDER,
    ORDER_RESPONSE,
    CREDIT_NOTE,
    DEBIT_NOTE,
    QUOTATION,
    DESPATCH_ADVICE,
    RECEIPT_ADVICE,
    STATEMENT,
    REMITTANCE_ADVICE,
    CATALOGUE,
    APPLICATION_RESPONSE
}

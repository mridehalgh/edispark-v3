# EDI Spark document triage

This runbook distils the document correction, route qualification and AS2 operations guidance. Apply it to recorded evidence and preserve the original exchange history.

## BQ_VENDOR_NUMBER_REQUIRED
In B&Q acknowledgement details, enter the B&Q-assigned vendor number (one to seven digits, including any leading zeros). Verify it against the purchase order or supplier records. Validate the corrected draft before sending.

## BQ_ACK_DECISION_UNSUPPORTED RESPONSE_STATUS_CONFLICT
This B&Q profile supports full acceptance only. Check the actual decision and quantities against the purchase order. If you need to amend or reject the order, ask an integration administrator for a qualified route for that response.

## BQ_DELIVERY_DATE_REQUIRED BQ_ITEM_EAN_REQUIRED BQ_WHOLE_QUANTITY_REQUIRED BQ_ORDER_UNIT_REQUIRED BQ_LINE_REFERENCE_DUPLICATE BQ_ORDER_VERSION_REFERENCE
Compare the indicated date, product EAN, quantity, unit or line reference with the purchase order. Use the verified value in a corrected draft, then validate it.

## BQ_BUYER_IDENTITY BQ_IDENTIFIER_SCHEME BQ_CUSTOMER_NAME SOURCE_IDENTIFIER_INVALID
Verify the supplier and B&Q entity details against the purchase order and trading agreement. Use the confirmed names, GLNs and identifier schemes in a corrected draft.

## BQ_ITEM_SCHEME TARGET_LINE_COUNT TARGET_REQUIRED TARGET_REPRESENTATION PARTNER_PROFILE_INVALID
Check the recorded field against the trading partner profile. Complete the required values and check formatting or field length before validating the corrected draft.

## DOCUMENT_NOT_FINAL
Open the document, resolve its validation findings, and finalise it before sending. Cancelled documents require a new eligible document.

## DOCUMENT
Correct the indicated field using verified source records. Validate the corrected draft before sending. Keep the original document and exchange for traceability.

## CONFIGURATION PARTNER_UNAVAILABLE DOCUMENT_TYPE_MISMATCH PARTNER_MISMATCH TARGET_QUALIFICATION_STALE PROFILE_UNAVAILABLE TARGET_CONTRACT_UNAVAILABLE SOURCE_VERSION_UNSUPPORTED
Ask a workspace administrator to check the active partner, document type, route and pinned revisions. If the qualification is stale, validate the mapping against the partner contract and requalify the agreement before another submission.

## MAPPING MAPPING_VALIDATION_FAILED
Ask a mapping administrator to review how the indicated source fields map to the partner format. Publish and qualify a tested mapping revision before submitting again.

## VALIDATION_UNAVAILABLE
The readiness check could not complete. Run readiness again from Send to partner. If it still fails, ask a workspace administrator to inspect the processing timeline.

## RETRY_SCHEDULED
A retry is already scheduled. Check the next attempt in Files and let it run. Escalate if the attempts are exhausted or the error requires a connection change.

## AWAITING_RECEIPT AWAITING_MDN
Check for the expected receipt and any separately required acknowledgement. Confirm the recorded outcome before considering a resend.

## RECEIPT_REJECTED
Ask an integration administrator to verify the receipt disposition, AS2 identities, certificates and message integrity check. Preserve the original transfer and correct the cause before any resend.

## TRANSFER_REVIEW
Ask an integration administrator to open the linked transfer and inspect the last attempt, receipt and connection settings. Resolve certificate, signing, permission or configuration failures before considering a resend.

## CONNECTION_ACCESS
Ask an integration administrator to verify the connection permissions and the partner’s endpoint configuration before another attempt.

## CONNECTION_REQUEST
Check the partner endpoint, AS2 identifiers and supported request format with an integration administrator before trying again.

## MESSAGE_REJECTED
Open the linked file and inspect the recorded parsing or validation finding. Ask for a corrected interchange if the source file is invalid. Keep the original file for investigation.

## FAILED
Open the processing timeline and inspect the last recorded stage. Check whether a transfer was created and whether the failure is retryable before taking another action.

## NEEDS_REVIEW
Open the processing timeline and review the latest finding. If the cause is still missing, share the document reference and stage with a workspace administrator.

## PROCESSING
Check the processing timeline for the latest stage. If it remains unchanged, ask a workspace administrator to investigate before submitting again.

## DELIVERED
Check the timeline for any required technical acknowledgement and retailer acceptance. A recorded transport outcome does not establish either of those outcomes.

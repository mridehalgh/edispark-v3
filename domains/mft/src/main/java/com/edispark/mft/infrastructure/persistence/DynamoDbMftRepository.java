package com.edispark.mft.infrastructure.persistence;

import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferAttempt;
import com.edispark.mft.domain.model.TransferDirection;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.domain.repository.MftRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

public final class DynamoDbMftRepository implements MftRepository {
    private static final String WORK_INDEX = "work-index";
    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    public DynamoDbMftRepository(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public PartnerProfile savePartner(PartnerProfile partner) {
        Map<String, AttributeValue> partnerItem = entity(partner.tenantId(), "PARTNER#" + partner.id(), partner);
        Map<String, AttributeValue> owner = Map.of(
                "pk", string(inboundOwnerPk(partner.localAs2Id(), partner.remoteAs2Id())),
                "sk", string("OWNER"),
                "tenantId", string(partner.tenantId()),
                "partnerId", string(partner.id().toString()));
        try {
            dynamoDb.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    put(owner, "attribute_not_exists(pk)"),
                    put(partnerItem, "attribute_not_exists(pk) AND attribute_not_exists(sk)"))
                    .build());
            return partner;
        } catch (TransactionCanceledException exception) {
            throw new IllegalArgumentException("The AS2 identity pair is already assigned to a workspace", exception);
        }
    }

    @Override
    public List<PartnerProfile> listPartners(String tenantId) {
        return queryPrefix(tenantId, "PARTNER#").stream().map(item -> read(item, PartnerProfile.class))
                .sorted(Comparator.comparing(PartnerProfile::name)).toList();
    }

    @Override
    public Optional<PartnerProfile> findPartner(String tenantId, UUID partnerId) {
        return get(tenantId, "PARTNER#" + partnerId).map(item -> read(item, PartnerProfile.class));
    }

    @Override
    public Optional<PartnerProfile> findInboundPartner(String localAs2Id, String remoteAs2Id) {
        Map<String, AttributeValue> owner = dynamoDb.getItem(GetItemRequest.builder().tableName(tableName)
                .consistentRead(true).key(key(inboundOwnerPk(localAs2Id, remoteAs2Id), "OWNER")).build()).item();
        if (owner.isEmpty()) return Optional.empty();
        return findPartner(owner.get("tenantId").s(), UUID.fromString(owner.get("partnerId").s()));
    }

    @Override
    public Transfer saveOutboundIfAbsent(Transfer transfer) {
        Map<String, AttributeValue> transferItem = transferEntity(transfer);
        Map<String, AttributeValue> marker = Map.of(
                "pk", string(tenantPk(transfer.tenantId())),
                "sk", string("IDEMPOTENCY#" + sha256(transfer.idempotencyKey())),
                "transferId", string(transfer.id().toString()));
        Map<String, AttributeValue> messageMarker = Map.of(
                "pk", string(tenantPk(transfer.tenantId())),
                "sk", string("OUTBOUND_MESSAGE#" + sha256(transfer.as2MessageId())),
                "transferId", string(transfer.id().toString()));
        try {
            dynamoDb.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    put(marker, "attribute_not_exists(pk) AND attribute_not_exists(sk)"),
                    put(messageMarker, "attribute_not_exists(pk) AND attribute_not_exists(sk)"),
                    put(transferItem, "attribute_not_exists(pk) AND attribute_not_exists(sk)"))
                    .build());
            return transfer;
        } catch (TransactionCanceledException exception) {
            return findByIdempotencyKey(transfer.tenantId(), transfer.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Idempotency transaction conflicted", exception));
        }
    }

    @Override
    public Transfer saveInboundIfAbsent(Transfer transfer) {
        String markerKey = inboundMarker(transfer.as2MessageId(), transfer.payload().sha256());
        Map<String, AttributeValue> marker = Map.of(
                "pk", string(tenantPk(transfer.tenantId())), "sk", string(markerKey),
                "transferId", string(transfer.id().toString()));
        try {
            dynamoDb.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    put(marker, "attribute_not_exists(pk) AND attribute_not_exists(sk)"),
                    put(transferEntity(transfer), "attribute_not_exists(pk) AND attribute_not_exists(sk)"))
                    .build());
            return transfer;
        } catch (TransactionCanceledException exception) {
            return findInboundDuplicate(transfer.tenantId(), transfer.as2MessageId(), transfer.payload().sha256())
                    .orElseThrow(() -> new IllegalStateException("Inbound deduplication transaction conflicted", exception));
        }
    }

    @Override
    public Transfer save(Transfer transfer) {
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(transferEntity(transfer)).build());
        return transfer;
    }

    @Override
    public Optional<Transfer> findTransfer(String tenantId, TransferId id) {
        return get(tenantId, "TRANSFER#" + id).map(this::readTransfer);
    }

    @Override
    public Optional<Transfer> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return markerTransfer(tenantId, "IDEMPOTENCY#" + sha256(idempotencyKey));
    }

    @Override
    public Optional<Transfer> findOutboundByMessageId(String tenantId, String as2MessageId) {
        return markerTransfer(tenantId, "OUTBOUND_MESSAGE#" + sha256(as2MessageId));
    }

    @Override
    public Optional<Transfer> findInboundDuplicate(String tenantId, String as2MessageId, String sha256) {
        return markerTransfer(tenantId, inboundMarker(as2MessageId, sha256));
    }

    @Override
    public List<Transfer> listTransfers(String tenantId) {
        return queryPrefix(tenantId, "TRANSFER#").stream().map(this::readTransfer)
                .sorted(Comparator.comparing(Transfer::createdAt).reversed()).toList();
    }

    @Override
    public CertificateRecord saveCertificate(CertificateRecord certificate) {
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName)
                .item(entity(certificate.tenantId(), "CERTIFICATE#" + certificate.id(), certificate)).build());
        return certificate;
    }

    @Override
    public List<CertificateRecord> listCertificates(String tenantId) {
        return queryPrefix(tenantId, "CERTIFICATE#").stream().map(item -> read(item, CertificateRecord.class))
                .sorted(Comparator.comparing(CertificateRecord::notAfter)).toList();
    }

    @Override
    public Optional<CertificateRecord> findCertificate(String tenantId, UUID certificateId) {
        return get(tenantId, "CERTIFICATE#" + certificateId).map(item -> read(item, CertificateRecord.class));
    }

    @Override
    public List<Transfer> listPendingTransfers(Instant dueAt, int limit) {
        List<Transfer> result = new ArrayList<>();
        result.addAll(queryWork(TransferStatus.QUEUED, dueAt, limit));
        if (result.size() < limit) result.addAll(queryWork(TransferStatus.RETRY_SCHEDULED, dueAt, limit - result.size()));
        return result.stream().sorted(Comparator.comparing(Transfer::nextAttemptAt)).limit(limit).toList();
    }

    @Override
    public boolean tryAcquireDeliveryLease(String tenantId, TransferId transferId, String owner,
                                           Instant now, Instant expiresAt) {
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("lease expiry must be after now");
        Map<String, AttributeValue> lease = Map.of(
                "pk", string("LEASE#" + tenantId + "#" + transferId),
                "sk", string("DELIVERY"),
                "tenantId", string(tenantId),
                "transferId", string(transferId.toString()),
                "leaseOwner", string(owner),
                "leaseUntil", number(expiresAt.toEpochMilli()),
                "expiresAt", AttributeValue.builder().n(Long.toString(expiresAt.getEpochSecond())).build());
        try {
            dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(lease)
                    .conditionExpression("attribute_not_exists(pk) OR leaseUntil < :now")
                    .expressionAttributeValues(Map.of(":now", number(now.toEpochMilli())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    @Override
    public void releaseDeliveryLease(String tenantId, TransferId transferId, String owner) {
        try {
            dynamoDb.deleteItem(DeleteItemRequest.builder().tableName(tableName)
                    .key(key("LEASE#" + tenantId + "#" + transferId, "DELIVERY"))
                    .conditionExpression("leaseOwner = :owner")
                    .expressionAttributeValues(Map.of(":owner", string(owner)))
                    .build());
        } catch (ConditionalCheckFailedException ignored) {
            // An expired lease may already belong to another worker; never delete that worker's lease.
        }
    }

    private List<Transfer> queryWork(TransferStatus status, Instant dueAt, int limit) {
        if (limit < 1) return List.of();
        return dynamoDb.query(QueryRequest.builder().tableName(tableName).indexName(WORK_INDEX)
                .keyConditionExpression("workPk = :status AND workSk <= :due")
                .expressionAttributeValues(Map.of(":status", string("STATUS#" + status),
                        ":due", string(dueAt + "#~")))
                .limit(limit).build()).items().stream().map(this::readTransfer).toList();
    }

    private Optional<Transfer> markerTransfer(String tenantId, String markerSk) {
        return get(tenantId, markerSk).flatMap(marker ->
                findTransfer(tenantId, TransferId.fromString(marker.get("transferId").s())));
    }

    private Optional<Map<String, AttributeValue>> get(String tenantId, String sk) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder().tableName(tableName)
                .consistentRead(true).key(key(tenantPk(tenantId), sk)).build()).item();
        return item.isEmpty() ? Optional.empty() : Optional.of(item);
    }

    private List<Map<String, AttributeValue>> queryPrefix(String tenantId, String prefix) {
        return dynamoDb.query(QueryRequest.builder().tableName(tableName)
                .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                .expressionAttributeValues(Map.of(":pk", string(tenantPk(tenantId)), ":prefix", string(prefix)))
                .build()).items();
    }

    private Map<String, AttributeValue> entity(String tenantId, String sk, Object value) {
        return Map.of("pk", string(tenantPk(tenantId)), "sk", string(sk), "data", string(write(value)));
    }

    private Map<String, AttributeValue> transferEntity(Transfer transfer) {
        Map<String, AttributeValue> item = new java.util.HashMap<>(entity(transfer.tenantId(),
                "TRANSFER#" + transfer.id(), TransferSnapshot.from(transfer)));
        if (transfer.status() == TransferStatus.QUEUED || transfer.status() == TransferStatus.RETRY_SCHEDULED) {
            item.put("workPk", string("STATUS#" + transfer.status()));
            item.put("workSk", string(transfer.nextAttemptAt() + "#" + transfer.id()));
        }
        return Map.copyOf(item);
    }

    private Transfer readTransfer(Map<String, AttributeValue> item) {
        return read(item, TransferSnapshot.class).toTransfer();
    }

    private <T> T read(Map<String, AttributeValue> item, Class<T> type) {
        try {
            return json.readValue(item.get("data").s(), type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored MFT metadata is invalid", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("MFT metadata could not be serialized", exception);
        }
    }

    private TransactWriteItem put(Map<String, AttributeValue> item, String condition) {
        return TransactWriteItem.builder().put(Put.builder().tableName(tableName).item(item)
                .conditionExpression(condition).build()).build();
    }

    private Map<String, AttributeValue> key(String pk, String sk) {
        return Map.of("pk", string(pk), "sk", string(sk));
    }

    private AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private String tenantPk(String tenantId) {
        return "TENANT#" + tenantId;
    }

    private String inboundOwnerPk(String localAs2Id, String remoteAs2Id) {
        return "AS2PAIR#" + sha256(localAs2Id.length() + ":" + localAs2Id + remoteAs2Id);
    }

    private String inboundMarker(String messageId, String payloadSha256) {
        return "INBOUND#" + sha256(messageId + "\u0000" + payloadSha256);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record TransferSnapshot(TransferId id, String tenantId, UUID partnerId,
            TransferDirection direction, String filename, String contentType, String as2MessageId,
            String idempotencyKey, TransferId resendOf, TransferStatus status, Instant createdAt,
            Instant updatedAt, Instant nextAttemptAt, PayloadRef payload, String mdnDisposition,
            String failureReason, String sentContentMic, List<TransferAttempt> attempts) {
        static TransferSnapshot from(Transfer transfer) {
            return new TransferSnapshot(transfer.id(), transfer.tenantId(), transfer.partnerId(), transfer.direction(),
                    transfer.filename(), transfer.contentType(), transfer.as2MessageId(), transfer.idempotencyKey(),
                    transfer.resendOf(), transfer.status(), transfer.createdAt(), transfer.updatedAt(),
                    transfer.nextAttemptAt(), transfer.payload(), transfer.mdnDisposition(), transfer.failureReason(),
                    transfer.sentContentMic(), transfer.attempts());
        }

        Transfer toTransfer() {
            return Transfer.restore(id, tenantId, partnerId, direction, filename, contentType, as2MessageId,
                    idempotencyKey, resendOf, status, createdAt, updatedAt, nextAttemptAt, payload,
                    mdnDisposition, failureReason, sentContentMic, attempts);
        }
    }
}

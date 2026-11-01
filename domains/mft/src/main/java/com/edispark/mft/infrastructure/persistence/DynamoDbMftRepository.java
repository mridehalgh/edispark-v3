package com.edispark.mft.infrastructure.persistence;

import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.MdnDelivery;
import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferAttempt;
import com.edispark.mft.domain.model.TransferDirection;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.domain.repository.MftRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    public PartnerProfile updatePartner(PartnerProfile partner) {
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName)
                .item(entity(partner.tenantId(), "PARTNER#" + partner.id(), partner))
                .conditionExpression("attribute_exists(pk) AND attribute_exists(sk)").build());
        return partner;
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
        String markerKey = inboundMarker(transfer.as2MessageId());
        Map<String, AttributeValue> marker = Map.of(
                "pk", string(tenantPk(transfer.tenantId())), "sk", string(markerKey),
                "transferId", string(transfer.id().toString()),
                "payloadSha256", string(transfer.payload().sha256()));
        try {
            dynamoDb.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    put(marker, "attribute_not_exists(pk) AND attribute_not_exists(sk)"),
                    put(transferEntity(transfer), "attribute_not_exists(pk) AND attribute_not_exists(sk)"))
                    .build());
            return transfer;
        } catch (TransactionCanceledException exception) {
            Transfer original = findInboundByMessageId(transfer.tenantId(), transfer.as2MessageId())
                    .orElseThrow(() -> new IllegalStateException("Inbound deduplication transaction conflicted", exception));
            if (original.payload() == null || !original.payload().sha256().equals(transfer.payload().sha256())) {
                throw new com.edispark.mft.application.As2ReplayConflictException();
            }
            return original;
        }
    }

    @Override
    public Transfer save(Transfer transfer) {
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(transferEntity(transfer)).build());
        return transfer;
    }

    @Override
    public boolean saveIfStatus(Transfer transfer, TransferStatus expectedStatus) {
        try {
            dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(transferEntity(transfer))
                    .conditionExpression("#status = :expectedStatus")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":expectedStatus", string(expectedStatus.name())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
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
        return findInboundByMessageId(tenantId, as2MessageId)
                .filter(item -> item.payload() != null && item.payload().sha256().equals(sha256));
    }

    @Override
    public Optional<Transfer> findInboundByMessageId(String tenantId, String as2MessageId) {
        return markerTransfer(tenantId, inboundMarker(as2MessageId));
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
        result.addAll(queryWork(TransferStatus.PROCESSING, dueAt, limit));
        if (result.size() < limit) result.addAll(queryWork(TransferStatus.RETRY_SCHEDULED, dueAt, limit - result.size()));
        if (result.size() < limit) result.addAll(queryWork(TransferStatus.QUEUED, dueAt, limit - result.size()));
        return result.stream().sorted(Comparator.comparing(Transfer::nextAttemptAt)).limit(limit).toList();
    }

    @Override
    public boolean tryAcquireDeliveryLease(String tenantId, TransferId transferId, String owner,
                                           Instant now, Instant expiresAt) {
        return tryAcquireLease("DELIVERY", tenantId, transferId.toString(), owner, now, expiresAt);
    }

    private boolean tryAcquireLease(String purpose, String tenantId, String resourceId, String owner,
                                    Instant now, Instant expiresAt) {
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("lease expiry must be after now");
        Map<String, AttributeValue> lease = Map.of(
                "pk", string("LEASE#" + tenantId + "#" + resourceId),
                "sk", string(purpose),
                "tenantId", string(tenantId),
                "resourceId", string(resourceId),
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
        releaseLease("DELIVERY", tenantId, transferId.toString(), owner);
    }

    private void releaseLease(String purpose, String tenantId, String resourceId, String owner) {
        try {
            dynamoDb.deleteItem(DeleteItemRequest.builder().tableName(tableName)
                    .key(key("LEASE#" + tenantId + "#" + resourceId, purpose))
                    .conditionExpression("leaseOwner = :owner")
                    .expressionAttributeValues(Map.of(":owner", string(owner)))
                    .build());
        } catch (ConditionalCheckFailedException ignored) {
            // An expired lease may already belong to another worker; never delete that worker's lease.
        }
    }

    @Override
    public MdnDelivery saveMdnDelivery(MdnDelivery delivery) {
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(mdnDeliveryEntity(delivery)).build());
        return delivery;
    }

    @Override
    public boolean saveMdnDeliveryIfAttempts(MdnDelivery delivery, int expectedAttempts) {
        try {
            dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(mdnDeliveryEntity(delivery))
                    .conditionExpression("#status = :pending AND attempts = :expected")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(":pending", string("PENDING"),
                            ":expected", number(expectedAttempts))).build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    @Override
    public MdnDelivery saveMdnDeliveryIfAbsent(MdnDelivery delivery) {
        try {
            dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(mdnDeliveryEntity(delivery))
                    .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build());
            return delivery;
        } catch (ConditionalCheckFailedException exception) {
            return get(delivery.tenantId(), "MDN_DELIVERY#" + delivery.id())
                    .map(item -> read(item, MdnDelivery.class))
                    .orElseThrow(() -> new IllegalStateException("MDN outbox transaction conflicted", exception));
        }
    }

    private Map<String, AttributeValue> mdnDeliveryEntity(MdnDelivery delivery) {
        Map<String, AttributeValue> item = new java.util.HashMap<>(entity(delivery.tenantId(),
                "MDN_DELIVERY#" + delivery.id(), delivery));
        item.put("status", string(delivery.status().name()));
        item.put("attempts", number(delivery.attempts()));
        if (delivery.status() == com.edispark.mft.domain.model.MdnDeliveryStatus.PENDING) {
            item.put("workPk", string("MDN#PENDING"));
            item.put("workSk", string(delivery.nextAttemptAt() + "#" + delivery.id()));
        }
        return Map.copyOf(item);
    }

    @Override
    public List<MdnDelivery> listPendingMdnDeliveries(Instant dueAt, int limit) {
        if (limit < 1) return List.of();
        return dynamoDb.query(QueryRequest.builder().tableName(tableName).indexName(WORK_INDEX)
                .keyConditionExpression("workPk = :status AND workSk <= :due")
                .expressionAttributeValues(Map.of(":status", string("MDN#PENDING"),
                        ":due", string(dueAt + "#~"))).limit(limit).build()).items().stream()
                .map(item -> read(item, MdnDelivery.class)).toList();
    }

    @Override
    public List<MdnDelivery> listMdnDeliveries(String tenantId) {
        return queryPrefix(tenantId, "MDN_DELIVERY#").stream()
                .map(item -> read(item, MdnDelivery.class))
                .sorted(Comparator.comparing(MdnDelivery::createdAt).reversed()).toList();
    }

    @Override
    public boolean tryAcquireMdnLease(String tenantId, UUID deliveryId, String owner,
                                      Instant now, Instant expiresAt) {
        return tryAcquireLease("MDN", tenantId, deliveryId.toString(), owner, now, expiresAt);
    }

    @Override
    public void releaseMdnLease(String tenantId, UUID deliveryId, String owner) {
        releaseLease("MDN", tenantId, deliveryId.toString(), owner);
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
        item.put("status", string(transfer.status().name()));
        if (transfer.status() == TransferStatus.QUEUED || transfer.status() == TransferStatus.RETRY_SCHEDULED
                || transfer.status() == TransferStatus.PROCESSING) {
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
            String data = item.get("data").s();
            if (type == PartnerProfile.class) {
                ObjectNode partner = (ObjectNode) json.readTree(data);
                migrateLegacyPartner(partner);
                return json.treeToValue(partner, type);
            }
            return json.readValue(data, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored MFT metadata is invalid", exception);
        }
    }

    static void migrateLegacyPartner(ObjectNode partner) {
        if (!partner.hasNonNull("partnerSigningCertificateId") && partner.hasNonNull("partnerCertificateId")) {
            partner.set("partnerSigningCertificateId", partner.get("partnerCertificateId"));
        }
        if (!partner.hasNonNull("partnerEncryptionCertificateId") && partner.hasNonNull("partnerCertificateId")) {
            partner.set("partnerEncryptionCertificateId", partner.get("partnerCertificateId"));
        }
        partner.remove("partnerCertificateId");
        if (!partner.hasNonNull("status")) partner.put("status", "ACTIVE");
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

    private String inboundMarker(String messageId) {
        return "INBOUND_MESSAGE#" + sha256(messageId);
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

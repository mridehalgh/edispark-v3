package com.edispark.mft.application;

import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.SecretStore;
import io.cottn.core.tenancy.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Enumeration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class CertificateService {
    private final MftRepository repository;
    private final SecretStore secretStore;
    private final Clock clock;
    private final boolean privateKeyUploadAllowed;
    private final byte[] managedPublicIdentity;

    public CertificateService(MftRepository repository, SecretStore secretStore, Clock clock) {
        this(repository, secretStore, clock, true);
    }

    public CertificateService(MftRepository repository, SecretStore secretStore, Clock clock,
                              boolean privateKeyUploadAllowed) {
        this.repository = repository;
        this.secretStore = secretStore;
        this.clock = clock;
        this.privateKeyUploadAllowed = privateKeyUploadAllowed;
        this.managedPublicIdentity = null;
    }

    public CertificateService(MftRepository repository, SecretStore secretStore, Clock clock,
                              byte[] managedPublicIdentity) {
        this.repository = repository;
        this.secretStore = secretStore;
        this.clock = clock;
        this.privateKeyUploadAllowed = false;
        this.managedPublicIdentity = managedPublicIdentity.clone();
    }

    public CertificateRecord upload(String name, CertificateKind kind, byte[] bytes, char[] password) throws IOException {
        if (kind == CertificateKind.LOCAL_IDENTITY && !privateKeyUploadAllowed && password.length > 0) {
            throw new IllegalArgumentException("Private-key upload is disabled; register an AWS KMS identity instead");
        }
        ParsedCertificate parsed = kind == CertificateKind.LOCAL_IDENTITY && privateKeyUploadAllowed
                ? parseIdentity(bytes, password)
                : parsePublic(bytes);
        if (kind == CertificateKind.LOCAL_IDENTITY && managedPublicIdentity != null
                && !fingerprint(parsed.certificate()).equals(fingerprint(parsePublic(managedPublicIdentity).certificate()))) {
            throw new IllegalArgumentException("Local identity must match the configured AWS KMS signing certificate");
        }
        if (kind == CertificateKind.LOCAL_IDENTITY && privateKeyUploadAllowed && !parsed.hasPrivateKey()) {
            throw new IllegalArgumentException("Local identity must contain a private key");
        }
        try {
            parsed.certificate().checkValidity(java.util.Date.from(clock.instant()));
        } catch (java.security.cert.CertificateException exception) {
            throw new IllegalArgumentException("Certificate is not currently valid", exception);
        }
        String tenantId = TenantContext.getCurrentTenant().asString();
        String secretRef = secretStore.put(tenantId, name, encodeMaterial(bytes, new String(password), parsed.alias()));
        X509Certificate certificate = parsed.certificate();
        CertificateRecord record = new CertificateRecord(UUID.randomUUID(), tenantId, name, kind,
                fingerprint(certificate), certificate.getSubjectX500Principal().getName(),
                certificate.getIssuerX500Principal().getName(), certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant(), parsed.hasPrivateKey(), toPem(certificate), secretRef,
                clock.instant());
        return repository.saveCertificate(record);
    }

    public List<CertificateRecord> list() {
        String tenantId = TenantContext.getCurrentTenant().asString();
        ensureManagedIdentity(tenantId);
        return repository.listCertificates(tenantId);
    }

    public CertificateMaterial load(UUID certificateId) throws IOException {
        return load(TenantContext.getCurrentTenant().asString(), certificateId);
    }

    public CertificateMaterial load(String tenantId, UUID certificateId) throws IOException {
        CertificateRecord record = repository.findCertificate(tenantId, certificateId)
                .orElseThrow(() -> new MftNotFoundException("Certificate was not found"));
        if (record.secretRef().startsWith("public:")) {
            return new CertificateMaterial(record.publicCertificatePem().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    "", "");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(secretStore.get(tenantId, record.secretRef())))) {
            int version = input.readInt();
            if (version != 1) throw new IOException("Unsupported certificate material version");
            String alias = input.readUTF();
            String password = input.readUTF();
            int length = input.readInt();
            if (length < 1 || length > 20 * 1024 * 1024) throw new IOException("Invalid certificate material length");
            return new CertificateMaterial(input.readNBytes(length), password, alias);
        }
    }

    public void ensureManagedIdentity(String tenantId) {
        if (managedPublicIdentity == null) return;
        ParsedCertificate parsed = parsePublic(managedPublicIdentity);
        String fingerprint = fingerprint(parsed.certificate());
        boolean exists = repository.listCertificates(tenantId).stream()
                .anyMatch(item -> item.kind() == CertificateKind.LOCAL_IDENTITY
                        && item.fingerprintSha256().equals(fingerprint));
        if (exists) return;
        X509Certificate certificate = parsed.certificate();
        UUID id = UUID.nameUUIDFromBytes((tenantId + "\u0000" + fingerprint)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repository.saveCertificate(new CertificateRecord(id, tenantId, "EdiSpark AS2 signing",
                CertificateKind.LOCAL_IDENTITY, fingerprint,
                certificate.getSubjectX500Principal().getName(), certificate.getIssuerX500Principal().getName(),
                certificate.getNotBefore().toInstant(), certificate.getNotAfter().toInstant(), false,
                toPem(certificate), "public:kms-managed-signing", clock.instant()));
    }

    private ParsedCertificate parseIdentity(byte[] bytes, char[] password) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(bytes), password);
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (store.getCertificate(alias) instanceof X509Certificate certificate) {
                    PrivateKey privateKey = store.getKey(alias, password) instanceof PrivateKey key ? key : null;
                    return new ParsedCertificate(certificate, privateKey != null, alias);
                }
            }
            throw new IllegalArgumentException("PKCS#12 file does not contain an X.509 certificate");
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("PKCS#12 file or password is invalid", exception);
        }
    }

    private ParsedCertificate parsePublic(byte[] bytes) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return new ParsedCertificate((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes)), false, "");
        } catch (Exception exception) {
            throw new IllegalArgumentException("Certificate file is invalid", exception);
        }
    }

    private String fingerprint(X509Certificate certificate) {
        try {
            return HexFormat.ofDelimiter(":").withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate certificate fingerprint", exception);
        }
    }

    private String toPem(X509Certificate certificate) {
        try {
            String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(certificate.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
        } catch (java.security.cert.CertificateEncodingException exception) {
            throw new IllegalStateException("Unable to encode public certificate", exception);
        }
    }

    private byte[] encodeMaterial(byte[] bytes, String password, String alias) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(1);
            output.writeUTF(alias);
            output.writeUTF(password);
            output.writeInt(bytes.length);
            output.write(bytes);
        }
        return buffer.toByteArray();
    }

    private record ParsedCertificate(X509Certificate certificate, boolean hasPrivateKey, String alias) {}
}

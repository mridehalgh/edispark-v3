package com.edispark.as2simulator;

import com.helger.phase2.client.AS2Client;
import com.helger.phase2.client.AS2ClientRequest;
import com.helger.phase2.client.AS2ClientResponse;
import com.helger.phase2.client.AS2ClientSettings;
import com.helger.phase2.crypto.ECompressionType;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.disposition.DispositionOptions;
import com.helger.security.keystore.EKeyStoreType;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {
    private final SimulatorStore store;
    private final String keyStore;
    private final String password;
    private final String senderId;
    private final String tradacomsSenderId;
    private final String senderAlias;
    private final String receiverId;
    private final String receiverCertificate;
    private final String receiverUrl;

    public SimulatorController(SimulatorStore store, @Value("${simulator.keystore}") String keyStore,
            @Value("${simulator.keystore-password}") String password,
            @Value("${simulator.sender-id}") String senderId, @Value("${simulator.sender-alias}") String senderAlias,
            @Value("${simulator.tradacoms-sender-id:SIMULATOR_TRADACOMS}") String tradacomsSenderId,
            @Value("${simulator.receiver-id}") String receiverId,
            @Value("${simulator.receiver-certificate}") String receiverCertificate,
            @Value("${simulator.receiver-url}") String receiverUrl) {
        this.store = store; this.keyStore = keyStore; this.password = password; this.senderId = senderId;
        this.senderAlias = senderAlias; this.tradacomsSenderId = tradacomsSenderId; this.receiverId = receiverId;
        this.receiverCertificate = receiverCertificate; this.receiverUrl = receiverUrl;
    }

    @GetMapping("/messages") public List<SimulatorStore.SimulatedMessage> messages() { return store.list(); }
    @GetMapping("/messages/{evidenceId}/content")
    public ResponseEntity<byte[]> content(@PathVariable java.util.UUID evidenceId) {
        return store.content(evidenceId)
                .map(content -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(content))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @DeleteMapping("/messages") @ResponseStatus(HttpStatus.NO_CONTENT) public void clear() { store.clear(); }

    @PostMapping("/send")
    public SendResult send(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "profile", defaultValue = "EDIFACT") String profile) throws Exception {
        String selectedSenderId = senderFor(profile);
        AS2ClientSettings settings = new AS2ClientSettings();
        settings.setKeyStore(EKeyStoreType.PKCS12, new File(keyStore), password);
        settings.setSenderData(selectedSenderId, "simulator@local.invalid", senderAlias);
        settings.setReceiverData(receiverId, receiverId, receiverUrl);
        byte[] certificateBytes = Files.readAllBytes(Path.of(receiverCertificate));
        settings.setReceiverCertificate((X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificateBytes)));
        settings.setPartnershipName(selectedSenderId + "_" + receiverId);
        settings.setMDNOptions(new DispositionOptions().setMICAlg(ECryptoAlgorithmSign.DIGEST_SHA_256)
                .setMICAlgImportance(DispositionOptions.IMPORTANCE_REQUIRED)
                .setProtocol(DispositionOptions.PROTOCOL_PKCS7_SIGNATURE)
                .setProtocolImportance(DispositionOptions.IMPORTANCE_REQUIRED));
        settings.setEncryptAndSign(ECryptoAlgorithmCrypt.CRYPT_AES128_GCM, ECryptoAlgorithmSign.DIGEST_SHA_256);
        settings.setCompress(ECompressionType.ZLIB, AS2ClientSettings.DEFAULT_COMPRESS_BEFORE_SIGNING);
        AS2ClientRequest request = new AS2ClientRequest("Simulator inbound test");
        request.setData(file.getBytes()).setFilename(file.getOriginalFilename()).setContentType(
                file.getContentType() == null ? "application/edifact" : file.getContentType());
        AS2ClientResponse response = new AS2Client().sendSynchronous(settings, request);
        if (response.hasException()) throw new IllegalStateException("AS2 simulation failed", response.getException());
        if (!isProcessedMdn(response)) {
            throw new IllegalStateException("MFT did not return a valid processed MDN");
        }
        store.add("OUTBOUND", response.getOriginalMessageID(), selectedSenderId, receiverId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes(), Instant.now());
        return new SendResult(response.getOriginalMessageID(), response.getMDNMessageID(), response.getMDNDisposition());
    }

    private boolean isProcessedMdn(AS2ClientResponse response) {
        String disposition = response.getMDNDisposition();
        return response.hasMDN() && disposition != null
                && disposition.toLowerCase(java.util.Locale.ROOT).matches(".*;\\s*processed\\s*$");
    }

    String senderFor(String profile) {
        return switch (profile.toUpperCase(java.util.Locale.ROOT)) {
            case "EDIFACT" -> senderId;
            case "TRADACOMS" -> tradacomsSenderId;
            default -> throw new IllegalArgumentException(
                    "Unknown simulator profile. Choose EDIFACT or TRADACOMS.");
        };
    }

    public record SendResult(String messageId, String mdnMessageId, String disposition) {}
}

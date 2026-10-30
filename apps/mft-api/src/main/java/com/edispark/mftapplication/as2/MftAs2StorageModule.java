package com.edispark.mftapplication.as2;

import com.edispark.mft.application.MftService;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.processor.module.AbstractProcessorModule;
import com.helger.phase2.processor.storage.IProcessorStorageModule;
import jakarta.mail.internet.MimeBodyPart;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MftAs2StorageModule extends AbstractProcessorModule implements IProcessorStorageModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MftAs2StorageModule.class);
    private final MftService mftService;

    MftAs2StorageModule(MftService mftService) {
        this.mftService = mftService;
    }

    @Override
    public boolean canHandle(String action, IMessage message, Map<String, Object> options) {
        return DO_STORE.equals(action);
    }

    @Override
    public void handle(String action, IMessage message, Map<String, Object> options) throws AS2Exception {
        try {
            MimeBodyPart body = message.getData();
            if (body == null) throw new IllegalArgumentException("AS2 message has no payload");
            String filename = body.getFileName() == null || body.getFileName().isBlank()
                    ? "inbound-" + safeMessageId(message) + ".edi" : body.getFileName();
            mftService.receiveInbound(message.getAS2To(), message.getAS2From(), safeMessageId(message),
                    filename, body.getContentType(), body.getInputStream().readAllBytes());
            LOGGER.info("Accepted inbound AS2 message {} for {}", safeMessageId(message), message.getAS2To());
        } catch (Exception exception) {
            throw new AS2Exception("Unable to retain inbound AS2 payload", exception);
        }
    }

    private String safeMessageId(IMessage message) {
        String messageId = message.getMessageID();
        return messageId == null || messageId.isBlank() ? "unknown-message" : messageId;
    }
}

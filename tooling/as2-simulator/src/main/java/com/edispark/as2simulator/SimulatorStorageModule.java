package com.edispark.as2simulator;

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.processor.module.AbstractProcessorModule;
import com.helger.phase2.processor.storage.IProcessorStorageModule;
import jakarta.mail.internet.MimeBodyPart;
import java.time.Instant;
import java.util.Map;

final class SimulatorStorageModule extends AbstractProcessorModule implements IProcessorStorageModule {
    private final SimulatorStore store;
    SimulatorStorageModule(SimulatorStore store) { this.store = store; }

    @Override public boolean canHandle(String action, IMessage message, Map<String, Object> options) {
        return DO_STORE.equals(action);
    }

    @Override public void handle(String action, IMessage message, Map<String, Object> options) throws AS2Exception {
        try {
            MimeBodyPart body = message.getData();
            byte[] content = body.getInputStream().readAllBytes();
            store.add(new SimulatorStore.SimulatedMessage("INBOUND", message.getMessageID(), message.getAS2From(),
                    message.getAS2To(), body.getFileName(), body.getContentType(), content.length, Instant.now()));
        } catch (Exception exception) {
            throw new AS2Exception("Simulator could not retain the payload", exception);
        }
    }
}

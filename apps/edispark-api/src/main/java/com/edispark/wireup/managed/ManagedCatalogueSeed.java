package com.edispark.wireup.managed;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import com.edispark.transformations.domain.GlobalMappingCatalog;
import com.edispark.transformations.infrastructure.TescoUkMappings;
import com.edispark.wireup.managed.ManagedTemplate.Capability;
import com.edispark.wireup.managed.ManagedTemplate.Definition;
import com.edispark.wireup.managed.ManagedTemplate.Transport;
import com.edispark.wireup.managed.ManagedTemplate.Verification;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("!openapi-export")
final class ManagedCatalogueSeed {
    static final UUID TESCO = UUID.fromString("c59a1bca-edd3-4b73-8b3e-c216984feb01");
    private static final UUID BQ = UUID.fromString("c59a1bca-edd3-4b73-8b3e-c216984feb02");
    private static final UUID NORTHSTAR = UUID.fromString("c59a1bca-edd3-4b73-8b3e-c216984feb03");
    private static final UUID HARBOUR = UUID.fromString("c59a1bca-edd3-4b73-8b3e-c216984feb04");
    private static final Transport AS2 = new Transport(MdnMode.SYNCHRONOUS, true, true, false);
    private final ManagedCatalogue catalogue;
    private final GlobalMappingCatalog mappings;
    private final Environment environment;

    ManagedCatalogueSeed(ManagedCatalogue catalogue, GlobalMappingCatalog mappings, Environment environment) {
        this.catalogue = catalogue;
        this.mappings = mappings;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    void reconcile() {
        catalogue.seed(TESCO, tesco());
        catalogue.seed(BQ, definition("B&Q · full-acceptance ACK v1.4", Verification.SIMULATOR_TESTED,
                InboundFileFailurePolicy.REJECT_FILE, List.of(capability(id(61)))));
        if (!environment.matchesProfiles("local")) return;
        catalogue.seed(NORTHSTAR, definition("Northstar Retail", Verification.LOCAL_DEMO,
                InboundFileFailurePolicy.REJECT_FILE, capabilities(24, 60, 42, 21, 54, 56)));
        catalogue.seed(HARBOUR, definition("Harbour Retail", Verification.LOCAL_DEMO,
                InboundFileFailurePolicy.KEEP_VALID_MESSAGES, capabilities(26, 41, 43, 23, 55, 57)));
    }

    Definition tesco() {
        return definition("Tesco UK · depot", Verification.SIMULATOR_TESTED, InboundFileFailurePolicy.REJECT_FILE,
                List.of(capability(TescoUkMappings.ORDER), capability(TescoUkMappings.INVOICE), capability(TescoUkMappings.DESPATCH)));
    }

    private Definition definition(String name, Verification verification, InboundFileFailurePolicy failurePolicy, List<Capability> capabilities) {
        return new Definition(name, verification, failurePolicy, AS2, capabilities);
    }

    private List<Capability> capabilities(int... suffixes) {
        return Arrays.stream(suffixes).mapToObj(ManagedCatalogueSeed::id).map(this::capability).toList();
    }

    private Capability capability(UUID id) {
        var mapping = mappings.find(id).orElseThrow();
        var match = mapping.compatibility();
        return new Capability(ExchangeDirection.valueOf(match.direction()), BusinessDocumentType.valueOf(match.documentType()),
                EdiStandard.valueOf(match.standard()), match.release(), match.messageType(), mapping.sourceSchemaId(),
                mapping.targetSchemaId(), id);
    }

    private static UUID id(int suffix) {
        return UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac" + suffix);
    }
}

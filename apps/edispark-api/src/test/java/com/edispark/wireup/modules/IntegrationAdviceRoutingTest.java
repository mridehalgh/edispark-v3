package com.edispark.wireup.modules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.edispark.application.exception.GlobalExceptionHandler;
import com.edispark.integrationapplication.config.IntegrationExceptionHandler;
import com.edispark.transformations.api.MappingController;
import com.edispark.transformations.application.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationAdviceRoutingTest {
    @Test void staleRetainedSourceIsAnActionableConflictBeforeTheGlobalFallback() throws Exception {
        var recovery = mock(MappingRecoveryService.class);
        when(recovery.test(any(), any())).thenThrow(new RetainedSourceChanged());
        var mvc = MockMvcBuilders.standaloneSetup(new MappingController(mock(MappingService.class), recovery))
                .setControllerAdvice(new GlobalExceptionHandler(), new IntegrationExceptionHandler()).build();
        String id = UUID.randomUUID().toString();
        String body = "{\"documentSetId\":\"" + id + "\",\"documentId\":\"" + id + "\",\"representationId\":\"" + id
                + "\",\"contentHash\":\"sha-256:" + "a".repeat(64) + "\"}";
        mvc.perform(post("/api/mappings/" + id + "/test-retained-source").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENT_SOURCE_CHANGED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }
}

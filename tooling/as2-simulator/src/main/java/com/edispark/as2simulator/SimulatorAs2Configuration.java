package com.edispark.as2simulator;

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.http.EHttpMethod;
import com.helger.phase2.cert.CertificateFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.SelfFillingPartnershipFactory;
import com.helger.phase2.processor.DefaultMessageProcessor;
import com.helger.phase2.servlet.AS2ReceiveXServletHandlerConstantSession;
import com.helger.phase2.servlet.AS2WebAppListener;
import com.helger.phase2.servlet.util.AS2ServletReceiverModule;
import com.helger.phase2.session.AS2Session;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.web.scope.mgr.WebScopeManager;
import com.helger.xservlet.AbstractXServlet;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulatorAs2Configuration {
    private final ServletContext servletContext;
    private AS2Session session;
    public SimulatorAs2Configuration(ServletContext servletContext) { this.servletContext = servletContext; }

    @Bean AS2Session simulatorSession(SimulatorStore store,
            @Value("${simulator.keystore}") String keyStore,
            @Value("${simulator.keystore-password}") String password) throws AS2Exception {
        AS2Session created = new AS2Session();
        CertificateFactory certificates = new CertificateFactory();
        certificates.setKeyStoreType(EKeyStoreType.PKCS12); certificates.setFilename(keyStore);
        certificates.setPassword(password); certificates.setSaveChangesToFile(false);
        certificates.initDynamicComponent(created, null); created.setCertificateFactory(certificates);
        SelfFillingPartnershipFactory partnerships = new SelfFillingPartnershipFactory();
        partnerships.initDynamicComponent(created, null); created.setPartnershipFactory(partnerships);
        DefaultMessageProcessor processor = new DefaultMessageProcessor();
        processor.setPendingMDNFolder(".local/as2-simulator/pending-mdn");
        processor.setPendingMDNInfoFolder(".local/as2-simulator/pending-mdn-info");
        processor.initDynamicComponent(created, null); created.setMessageProcessor(processor);
        AS2ServletReceiverModule receiver = new AS2ServletReceiverModule();
        receiver.setErrorDirectory(".local/as2-simulator/errors");
        receiver.setErrorFormat("$msg.headers.message-id$"); receiver.initDynamicComponent(created, null);
        processor.addModule(receiver);
        SimulatorStorageModule storage = new SimulatorStorageModule(store);
        storage.initDynamicComponent(created, null); processor.addModule(storage);
        processor.startActiveModules(); session = created; return created;
    }

    @Bean ServletRegistrationBean<SimulatorServlet> simulatorAs2Servlet(AS2Session simulatorSession) {
        if (!WebScopeManager.isGlobalScopePresent()) AS2WebAppListener.staticInit(servletContext);
        return new ServletRegistrationBean<>(new SimulatorServlet(simulatorSession), "/as2");
    }

    @PreDestroy void shutdown() {
        try { if (session != null) session.getMessageProcessor().stopActiveModules(); }
        catch (Exception ignored) { }
        finally { if (WebScopeManager.isGlobalScopePresent()) AS2WebAppListener.staticDestroy(); }
    }

    static final class SimulatorServlet extends AbstractXServlet {
        private final AS2Session session;
        SimulatorServlet(AS2Session session) { this.session = session; }
        @Override @OverridingMethodsMustInvokeSuper public void init() throws ServletException {
            settings().setMultipartEnabled(false);
            handlerRegistry().registerHandler(EHttpMethod.POST, new AS2ReceiveXServletHandlerConstantSession(session), false);
        }
    }
}

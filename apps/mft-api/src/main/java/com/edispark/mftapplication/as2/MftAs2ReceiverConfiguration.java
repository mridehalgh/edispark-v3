package com.edispark.mftapplication.as2;

import com.edispark.mft.application.CertificateService;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.repository.MftRepository;
import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.http.EHttpMethod;
import com.helger.phase2.cert.CertificateFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.processor.DefaultMessageProcessor;
import com.helger.phase2.processor.sender.AsynchMDNSenderModule;
import com.helger.phase2.servlet.AS2ReceiveXServletHandlerConstantSession;
import com.helger.phase2.servlet.AS2WebAppListener;
import com.helger.phase2.servlet.mdn.AS2MDNReceiveXServletHandlerConstantSession;
import com.helger.phase2.servlet.util.AS2ServletMDNReceiverModule;
import com.helger.phase2.servlet.util.AS2ServletReceiverModule;
import com.helger.phase2.session.AS2Session;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.web.scope.mgr.WebScopeManager;
import com.helger.xservlet.AbstractXServlet;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MftAs2ReceiverConfiguration {
    private final ServletContext servletContext;
    private AS2Session session;

    public MftAs2ReceiverConfiguration(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Bean
    AS2Session mftAs2Session(MftRepository repository, CertificateService certificateService,
                             MftService mftService) throws AS2Exception {
        AS2Session created = new AS2Session();
        CertificateFactory certificates = new CertificateFactory();
        certificates.setKeyStoreType(EKeyStoreType.PKCS12);
        certificates.setPassword("runtime-only");
        certificates.setSaveChangesToFile(false);
        certificates.initDynamicComponent(created, null);
        created.setCertificateFactory(certificates);

        MftAs2PartnershipFactory partnerships = new MftAs2PartnershipFactory(repository, certificateService, certificates);
        partnerships.initDynamicComponent(created, null);
        created.setPartnershipFactory(partnerships);

        DefaultMessageProcessor processor = new DefaultMessageProcessor();
        processor.setPendingMDNFolder(".local/mft/pending-mdn");
        processor.setPendingMDNInfoFolder(".local/mft/pending-mdn-info");
        processor.initDynamicComponent(created, null);
        created.setMessageProcessor(processor);
        addModules(created, processor, mftService);
        processor.startActiveModules();
        session = created;
        return created;
    }

    private void addModules(AS2Session session, DefaultMessageProcessor processor, MftService service) throws AS2Exception {
        AS2ServletReceiverModule receiver = new AS2ServletReceiverModule();
        receiver.setErrorDirectory(".local/mft/inbound-errors");
        receiver.setErrorFormat("$msg.sender.as2_id$-$msg.receiver.as2_id$-$date.uuuuMMddHHmmss$");
        receiver.initDynamicComponent(session, null);
        processor.addModule(receiver);
        AS2ServletMDNReceiverModule mdnReceiver = new AS2ServletMDNReceiverModule();
        mdnReceiver.initDynamicComponent(session, null);
        processor.addModule(mdnReceiver);
        AsynchMDNSenderModule mdnSender = new AsynchMDNSenderModule();
        mdnSender.initDynamicComponent(session, null);
        processor.addModule(mdnSender);
        MftAs2StorageModule storage = new MftAs2StorageModule(service);
        storage.initDynamicComponent(session, null);
        processor.addModule(storage);
    }

    @Bean
    ServletRegistrationBean<MftReceiveServlet> mftAs2Servlet(AS2Session mftAs2Session) {
        initializeWebScope();
        return new ServletRegistrationBean<>(new MftReceiveServlet(mftAs2Session), "/as2");
    }

    @Bean
    ServletRegistrationBean<MftMdnServlet> mftMdnServlet(AS2Session mftAs2Session) {
        initializeWebScope();
        return new ServletRegistrationBean<>(new MftMdnServlet(mftAs2Session), "/as2/mdn");
    }

    private void initializeWebScope() {
        if (!WebScopeManager.isGlobalScopePresent()) AS2WebAppListener.staticInit(servletContext);
    }

    @PreDestroy
    void shutdown() {
        try {
            if (session != null) session.getMessageProcessor().stopActiveModules();
        } catch (Exception ignored) {
            // Application is already stopping.
        } finally {
            if (WebScopeManager.isGlobalScopePresent()) AS2WebAppListener.staticDestroy();
        }
    }

    static final class MftReceiveServlet extends AbstractXServlet {
        private final AS2Session session;
        MftReceiveServlet(AS2Session session) { this.session = session; }
        @Override @OverridingMethodsMustInvokeSuper public void init() throws ServletException {
            settings().setMultipartEnabled(false);
            handlerRegistry().registerHandler(EHttpMethod.POST, new AS2ReceiveXServletHandlerConstantSession(session), false);
        }
    }

    static final class MftMdnServlet extends AbstractXServlet {
        private final AS2Session session;
        MftMdnServlet(AS2Session session) { this.session = session; }
        @Override @OverridingMethodsMustInvokeSuper public void init() throws ServletException {
            settings().setMultipartEnabled(false);
            handlerRegistry().registerHandler(EHttpMethod.POST, new AS2MDNReceiveXServletHandlerConstantSession(session), false);
        }
    }
}

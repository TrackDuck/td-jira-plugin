package com.trackduck.jira.applinks;

import com.atlassian.applinks.api.ApplicationId;
import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationType;
import com.atlassian.applinks.api.application.jira.JiraApplicationType;
import com.atlassian.applinks.spi.application.ApplicationIdUtil;
import com.atlassian.applinks.spi.link.ApplicationLinkDetails;
import com.atlassian.applinks.spi.link.MutatingApplicationLinkService;
import com.atlassian.applinks.spi.util.TypeAccessor;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.oauth.Consumer;
import com.atlassian.oauth.serviceprovider.ServiceProviderConsumerStore;
import com.atlassian.oauth.util.RSAKeys;
import com.atlassian.plugin.event.PluginEventListener;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.plugin.osgi.bridge.external.PluginRetrievalService;
import com.atlassian.sal.api.message.I18nResolver;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import static com.atlassian.applinks.core.auth.oauth.servlets.serviceprovider.AbstractConsumerServlet.OAUTH_INCOMING_CONSUMER_KEY;

public class TrackDuckApplicationLinkInstallerImpl implements TrackDuckApplicationLinkInstaller, InitializingBean, DisposableBean {

    private static final String TD_APPLICATION_LINK_URL = "trackduck.application.link.url";
    private static final String TD_APPLICATION_LINK_NAME = "trackduck.application.link.name";
    private static final String TD_CONSUMER_KEY = "trackduck.consumer.key";
    private static final String TD_CONSUMER_NAME = "trackduck.consumer.name";
    private static final String TD_CONSUMER_PUBLIC_KEY = "trackduck.consumer.public.key";

    private EventPublisher eventPublisher;
    private PluginRetrievalService pluginRetrievalService;
    private MutatingApplicationLinkService applicationLinkService;
    private TypeAccessor typeAccessor;
    private ServiceProviderConsumerStore serviceProviderConsumerStore;
    private I18nResolver i18nResolver;

    public TrackDuckApplicationLinkInstallerImpl(EventPublisher eventPublisher,
                                                 PluginRetrievalService pluginRetrievalService,
                                                 MutatingApplicationLinkService applicationLinkService,
                                                 TypeAccessor typeAccessor,
                                                 ServiceProviderConsumerStore serviceProviderConsumerStore,
                                                 I18nResolver i18nResolver) {
        this.eventPublisher = eventPublisher;
        this.pluginRetrievalService = pluginRetrievalService;
        this.applicationLinkService = applicationLinkService;
        this.typeAccessor = typeAccessor;
        this.serviceProviderConsumerStore = serviceProviderConsumerStore;
        this.i18nResolver = i18nResolver;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }

    @PluginEventListener
    public void onPluginEvent(PluginEnabledEvent pluginEnabledEvent) throws InvalidKeySpecException, NoSuchAlgorithmException {
        if (pluginRetrievalService.getPlugin().equals(pluginEnabledEvent.getPlugin())) {
            if (!isApplicationLinkInstalled()) {
                installApplicationLink();
            }
        }
    }

    private boolean isApplicationLinkInstalled() {
        URI linkUri = URI.create(i18nResolver.getText(TD_APPLICATION_LINK_URL));

        for (ApplicationLink applicationLink : applicationLinkService.getApplicationLinks()) {
            if (applicationLink.getDisplayUrl().equals(linkUri)) {
                return true;
            }
        }

        return false;
    }

    private void installApplicationLink() throws InvalidKeySpecException, NoSuchAlgorithmException {
        //create application link
        URI linkUri = URI.create(i18nResolver.getText(TD_APPLICATION_LINK_URL));
        String linkName = i18nResolver.getText(TD_APPLICATION_LINK_NAME);

        ApplicationId applicationId = ApplicationIdUtil.generate(linkUri);
        ApplicationType type = findGenericApplicationType();
        ApplicationLinkDetails details = ApplicationLinkDetails.builder().name(linkName).displayUrl(linkUri).build();

        ApplicationLink link = applicationLinkService.addApplicationLink(applicationId, type, details);

        //create consumer (it's equivalent of Incoming Authentication - OAuth settings)
        String consumerKey = i18nResolver.getText(TD_CONSUMER_KEY);
        String consumerName = i18nResolver.getText(TD_CONSUMER_NAME);
        String consumerPublicKey = i18nResolver.getText(TD_CONSUMER_PUBLIC_KEY);

        PublicKey publicKey = RSAKeys.fromPemEncodingToPublicKey(consumerPublicKey);
        Consumer consumer = Consumer.key(consumerKey).name(consumerName).publicKey(publicKey).build();
        serviceProviderConsumerStore.put(consumer);

        //assign consumer to application link
        link.putProperty(OAUTH_INCOMING_CONSUMER_KEY, consumer.getKey());
    }

    private ApplicationType findGenericApplicationType() {
        //need GenericApplicationType but can not to use typeAccessor.getApplicationType(GenericApplicationType.class) because API bug
        ApplicationType applicationType = typeAccessor.getApplicationType(JiraApplicationType.class);

        //find GenericApplicationType from all enable application types
        for (ApplicationType enabledApplicationType : typeAccessor.getEnabledApplicationTypes()) {
            if (enabledApplicationType.getClass().getName().contains("GenericApplicationType")) {
                applicationType = enabledApplicationType;
                break;
            }
        }

        return applicationType;
    }

    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }
}
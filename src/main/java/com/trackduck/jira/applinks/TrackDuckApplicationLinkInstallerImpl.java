package com.trackduck.jira.applinks;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationType;
import com.atlassian.applinks.api.application.jira.JiraApplicationType;
import com.atlassian.applinks.spi.application.ApplicationIdUtil;
import com.atlassian.applinks.spi.link.ApplicationLinkDetails;
import com.atlassian.applinks.spi.link.MutatingApplicationLinkService;
import com.atlassian.applinks.spi.util.TypeAccessor;
import com.atlassian.oauth.Consumer;
import com.atlassian.oauth.serviceprovider.ServiceProviderConsumerStore;
import com.atlassian.oauth.util.RSAKeys;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Properties;

import static com.atlassian.applinks.core.auth.oauth.servlets.serviceprovider.AbstractConsumerServlet.OAUTH_INCOMING_CONSUMER_KEY;

public class TrackDuckApplicationLinkInstallerImpl implements TrackDuckApplicationLinkInstaller {

    private static final String PROPERTIES_FILE = "/18n_trackDuck.properties";

    private static final String TD_APPLICATION_LINK_URL = "track.duck.application.link.url";
    private static final String TD_APPLICATION_LINK_NAME = "track.duck.application.link.name";
    private static final String TD_CONSUMER_KEY = "track.duck.consumer.key";
    private static final String TD_CONSUMER_NAME = "track.duck.consumer.name";
    private static final String TD_CONSUMER_PUBLIC_KEY = "track.duck.consumer.public.key";

    private static URI APPLICATION_LINK_URI;
    private static String APPLICATION_LINK_NAME;
    private static String CONSUMER_KEY;
    private static String CONSUMER_NAME;
    private static String CONSUMER_PUBLIC_KEY;

    private MutatingApplicationLinkService applicationLinkService;
    private TypeAccessor typeAccessor;
    private ServiceProviderConsumerStore serviceProviderConsumerStore;

    public TrackDuckApplicationLinkInstallerImpl(MutatingApplicationLinkService applicationLinkService,
                                                 TypeAccessor typeAccessor,
                                                 ServiceProviderConsumerStore serviceProviderConsumerStore)
            throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        this.applicationLinkService = applicationLinkService;
        this.typeAccessor = typeAccessor;
        this.serviceProviderConsumerStore = serviceProviderConsumerStore;

        readPluginSettings();

        if (!isApplicationLinkInstalled()) {
            installApplicationLink();
        }
    }

    private static void readPluginSettings() throws IOException {
        Properties properties = new Properties();

        try (InputStream is = TrackDuckApplicationLinkInstallerImpl.class.getResourceAsStream(PROPERTIES_FILE)) {
            properties.load(is);
        }

        String linkUrl = properties.getProperty(TD_APPLICATION_LINK_URL);
        APPLICATION_LINK_NAME = properties.getProperty(TD_APPLICATION_LINK_NAME);
        APPLICATION_LINK_URI = URI.create(linkUrl);

        CONSUMER_KEY = properties.getProperty(TD_CONSUMER_KEY);
        CONSUMER_NAME = properties.getProperty(TD_CONSUMER_NAME);
        CONSUMER_PUBLIC_KEY = properties.getProperty(TD_CONSUMER_PUBLIC_KEY);
    }

    private boolean isApplicationLinkInstalled() {
        for (ApplicationLink applicationLink : applicationLinkService.getApplicationLinks()) {
            if (applicationLink.getDisplayUrl().equals(APPLICATION_LINK_URI)) {
                return true;
            }
        }

        return false;
    }

    private void installApplicationLink() throws InvalidKeySpecException, NoSuchAlgorithmException {
        //create application link
        ApplicationType type = findGenericApplicationType(typeAccessor);
        ApplicationLinkDetails details = ApplicationLinkDetails.builder().name(APPLICATION_LINK_NAME).displayUrl(APPLICATION_LINK_URI).build();
        ApplicationLink link = applicationLinkService.addApplicationLink(ApplicationIdUtil.generate(APPLICATION_LINK_URI), type, details);

        //create consumer (it's equivalent of Incoming Authentication - OAuth settings)
        PublicKey pKey = RSAKeys.fromPemEncodingToPublicKey(CONSUMER_PUBLIC_KEY);
        Consumer consumer = Consumer.key(CONSUMER_KEY).name(CONSUMER_NAME).publicKey(pKey).build();
        serviceProviderConsumerStore.put(consumer);

        //assign consumer to application link
        link.putProperty(OAUTH_INCOMING_CONSUMER_KEY, consumer.getKey());
    }

    private ApplicationType findGenericApplicationType(TypeAccessor typeAccessor) {
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
}
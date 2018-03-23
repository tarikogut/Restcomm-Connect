/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.restcomm.connect.sms.smpp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipURI;

import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.configuration.sets.RcmlserverConfigurationSet;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.util.UriUtils;
import org.restcomm.connect.core.service.api.NumberSelectorService;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ApplicationsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.common.OrganizationUtil;
import org.restcomm.connect.dao.entities.Application;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.extension.api.ExtensionResponse;
//import org.restcomm.connect.extension.api.ExtensionRequest;
//import org.restcomm.connect.extension.api.ExtensionResponse;
import org.restcomm.connect.extension.api.ExtensionType;
import org.restcomm.connect.extension.api.IExtensionCreateSmsSessionRequest;
import org.restcomm.connect.extension.api.IExtensionFeatureAccessRequest;
import org.restcomm.connect.extension.api.RestcommExtensionException;
import org.restcomm.connect.extension.api.RestcommExtensionGeneric;
import org.restcomm.connect.extension.controller.ExtensionController;
import org.restcomm.connect.http.client.rcmlserver.resolver.RcmlserverResolver;
import org.restcomm.connect.interpreter.StartInterpreter;
import org.restcomm.connect.monitoringservice.MonitoringService;
import org.restcomm.connect.sms.SmsSession;
import org.restcomm.connect.sms.api.CreateSmsSession;
import org.restcomm.connect.sms.api.DestroySmsSession;
import org.restcomm.connect.sms.api.SmsServiceResponse;
import org.restcomm.connect.telephony.api.FeatureAccessRequest;
import org.restcomm.smpp.parameter.TlvSet;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;

//import org.restcomm.connect.extension.api.ExtensionRequest;
//import org.restcomm.connect.extension.api.ExtensionResponse;
public class SmppMessageHandler extends RestcommUntypedActor {

    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);
    private final ServletContext servletContext;
    private final DaoManager storage;
    private final Configuration configuration;
    private final SipFactory sipFactory;
    private final ActorRef monitoringService;
    private final NumberSelectorService numberSelector;
    //List of extensions for SmsService
    List<RestcommExtensionGeneric> extensions;

    public SmppMessageHandler(final ServletContext servletContext) {
        this.servletContext = servletContext;
        this.storage = (DaoManager) servletContext.getAttribute(DaoManager.class.getName());
        this.configuration = (Configuration) servletContext.getAttribute(Configuration.class.getName());
        this.sipFactory = (SipFactory) servletContext.getAttribute(SipFactory.class.getName());
        this.monitoringService = (ActorRef) servletContext.getAttribute(MonitoringService.class.getName());
        numberSelector = (NumberSelectorService)servletContext.getAttribute(NumberSelectorService.class.getName());
        //FIXME:Should new ExtensionType.SmppMessageHandler be defined?
        extensions = ExtensionController.getInstance().getExtensions(ExtensionType.SmsService);
        if (logger.isInfoEnabled()) {
            logger.info("SmsService extensions: " + (extensions != null ? extensions.size() : "0"));
        }
    }

    @Override
    public void onReceive(Object message) throws Exception {
        final UntypedActorContext context = getContext();
        final ActorRef sender = sender();
        final ActorRef self = self();
        ExtensionController ec = ExtensionController.getInstance();
        if (message instanceof SmppInboundMessageEntity) {
            if (logger.isInfoEnabled()) {
                logger.info("SmppMessageHandler processing Inbound Message " + message.toString());
            }
            inbound((SmppInboundMessageEntity) message);
        } else if (message instanceof SmppOutboundMessageEntity) {
            if (logger.isInfoEnabled()) {
                logger.info("SmppMessageHandler processing Outbound Message " + message.toString());
            }
            outbound((SmppOutboundMessageEntity) message);
        } else if (message instanceof CreateSmsSession) {
            IExtensionCreateSmsSessionRequest ier = (CreateSmsSession) message;
            ier.setConfiguration(this.configuration);
            ExtensionResponse executePreOutboundAction = ec.executePreOutboundAction(ier, this.extensions);
            if (executePreOutboundAction.isAllowed()) {
                CreateSmsSession createSmsSession = (CreateSmsSession) message;
                final ActorRef session = session(ier.getConfiguration(), OrganizationUtil.getOrganizationSidByAccountSid(storage, new Sid(createSmsSession.getAccountSid())));
                final SmsServiceResponse<ActorRef> response = new SmsServiceResponse<ActorRef>(session);
                sender.tell(response, self);
            } else {
                final SmsServiceResponse<ActorRef> response = new SmsServiceResponse(new RestcommExtensionException("Now allowed to create SmsSession"));
                sender.tell(response, self());
            }
            ec.executePostOutboundAction(ier, this.extensions);
        } else if (message instanceof DestroySmsSession) {
            final DestroySmsSession destroySmsSession = (DestroySmsSession) message;
            final ActorRef session = destroySmsSession.session();
            context.stop(session);
        }
    }

    private void inbound(final SmppInboundMessageEntity request) throws IOException {
        final ActorRef self = self();

        String to = request.getSmppTo();

        if (redirectToHostedSmsApp(self, request, storage.getAccountsDao(), storage.getApplicationsDao(), to)) {
            if (logger.isInfoEnabled()) {
                logger.info("SMPP Message Accepted - A Restcomm Hosted App is Found for Number : " + to);
            }
            return;
        } else {
            logger.warning("SMPP Message Rejected : No Restcomm Hosted App Found for inbound number : " + to);
        }
    }

    static final int ERROR_NOTIFICATION = 0;
    static final int WARNING_NOTIFICATION = 1;

    // used for sending warning and error logs to notification engine and to the console
    private void sendNotification(String errMessage, int errCode, String errType, boolean createNotification) {
        NotificationsDao notifications = storage.getNotificationsDao();
        Notification notification;

        if (errType == "warning") {
            logger.warning(errMessage); // send message to console
            if (createNotification) {
                notification = notification(WARNING_NOTIFICATION, errCode, errMessage);
                notifications.addNotification(notification);
            }
        } else if (errType == "error") {
            logger.error(errMessage); // send message to console
            if (createNotification) {
                notification = notification(ERROR_NOTIFICATION, errCode, errMessage);
                notifications.addNotification(notification);
            }
        } else if (errType == "info") {
            if(logger.isInfoEnabled()) {
                logger.info(errMessage); // send message to console
            }
        }
    }

    private Notification notification(final int log, final int error, final String message) {
        String version = configuration.subset("runtime-settings").getString("api-version");
        Sid accountId = new Sid("ACae6e420f425248d6a26948c17a9e2acf");
        //        Sid callSid = new Sid("CA00000000000000000000000000000000");
        final Notification.Builder builder = Notification.builder();
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        builder.setSid(sid);
        // builder.setAccountSid(accountId);
        builder.setAccountSid(accountId);
        //        builder.setCallSid(callSid);
        builder.setApiVersion(version);
        builder.setLog(log);
        builder.setErrorCode(error);
        final String base = configuration.subset("runtime-settings").getString("error-dictionary-uri");
        StringBuilder buffer = new StringBuilder();
        buffer.append(base);
        if (!base.endsWith("/")) {
            buffer.append("/");
        }
        buffer.append(error).append(".html");
        final URI info = URI.create(buffer.toString());
        builder.setMoreInfo(info);
        builder.setMessageText(message);
        final DateTime now = DateTime.now();
        builder.setMessageDate(now);
        try {
            builder.setRequestUrl(new URI(""));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        /**
         * if (response != null) { builder.setRequestUrl(request.getUri()); builder.setRequestMethod(request.getMethod());
         * builder.setRequestVariables(request.getParametersAsString()); }
         **/

        builder.setRequestMethod("");
        builder.setRequestVariables("");
        buffer = new StringBuilder();
        buffer.append("/").append(version).append("/Accounts/");
        buffer.append(accountId.toString()).append("/Notifications/");
        buffer.append(sid.toString());
        final URI uri = URI.create(buffer.toString());
        builder.setUri(uri);
        return builder.build();
    }

    private boolean redirectToHostedSmsApp(final ActorRef self, final SmppInboundMessageEntity request, final AccountsDao accounts,
            final ApplicationsDao applications, String id) throws IOException {
        boolean isFoundHostedApp = false;

        String to = request.getSmppTo();
        String phone = to;

        // Try to find an application defined for the phone number.
        IncomingPhoneNumber number = numberSelector.searchNumber(phone, null, null, null);
        try {
            if (number != null) {
                ExtensionController ec = ExtensionController.getInstance();
                IExtensionFeatureAccessRequest far = new FeatureAccessRequest(FeatureAccessRequest.Feature.INBOUND_SMS, number.getAccountSid());
                ExtensionResponse er = ec.executePreInboundAction(far, extensions);

                if (er.isAllowed()) {
                    ActorRef interpreter = null;

                    URI appUri = number.getSmsUrl();

                    final SmppInterpreterParams.Builder builder = new SmppInterpreterParams.Builder();
                    builder.setSmsService(self);
                    builder.setConfiguration(configuration);
                    builder.setStorage(storage);
                    builder.setAccountId(number.getAccountSid());
                    builder.setVersion(number.getApiVersion());
                    final Sid sid = number.getSmsApplicationSid();
                    boolean isApplicationNull = true;
                    if (sid != null) {
                        final Application application = applications.getApplication(sid);
                        if (application != null) {
                            isApplicationNull = false;
                            RcmlserverConfigurationSet rcmlserverConfig = RestcommConfiguration.getInstance().getRcmlserver();
                            RcmlserverResolver resolver = RcmlserverResolver.getInstance(rcmlserverConfig.getBaseUrl(), rcmlserverConfig.getApiPath());
                            builder.setUrl(UriUtils.resolve(resolver.resolveRelative(application.getRcmlUrl())));
                        }
                    }
                    if (isApplicationNull && appUri != null) {
                        builder.setUrl(UriUtils.resolve(appUri));
                    } else if (isApplicationNull) {
                        logger.warning("the matched number doesn't have SMS application attached, number: " + number.getPhoneNumber());
                        return false;
                    }
                    builder.setMethod(number.getSmsMethod());
                    URI appFallbackUrl = number.getSmsFallbackUrl();
                    if (appFallbackUrl != null) {
                        builder.setFallbackUrl(UriUtils.resolve(number.getSmsFallbackUrl()));
                        builder.setFallbackMethod(number.getSmsFallbackMethod());
                    }
                    final Props props = SmppInterpreter.props(builder.build());
                    interpreter = getContext().actorOf(props);

                    Sid organizationSid = storage.getOrganizationsDao().getOrganization(storage.getAccountsDao().getAccount(number.getAccountSid()).getOrganizationSid()).getSid();
                    if (logger.isDebugEnabled()) {
                        logger.debug("redirectToHostedSmsApp organizationSid = " + organizationSid);
                    }
                    Configuration cfg = this.configuration;
                    //Extension
                    final ActorRef session = session(cfg, organizationSid);
                    session.tell(request, self);
                    final StartInterpreter start = new StartInterpreter(session);
                    interpreter.tell(start, self);
                    isFoundHostedApp = true;
                    ec.executePostOutboundAction(far, extensions);
                } else {
                    if (logger.isDebugEnabled()) {
                        final String errMsg = "Inbound SMS is not Allowed";
                        logger.debug(errMsg);
                    }
                    String errMsg = "Inbound SMS to Number: " + number.getPhoneNumber()
                            + " is not allowed";
                    sendNotification(errMsg, 11001, "warning", true);
                    ec.executePostOutboundAction(far, extensions);
                    return false;
                }

            }
        } catch (Exception e) {
            logger.error("Error processing inbound SMPP Message. There is no locally hosted Restcomm app for the number :" + e);
        }
        return isFoundHostedApp;
    }

    @SuppressWarnings("unchecked")
    private SipURI outboundInterface() {
        SipURI result = null;
        final List<SipURI> uris = (List<SipURI>) servletContext.getAttribute(SipServlet.OUTBOUND_INTERFACES);
        for (final SipURI uri : uris) {
            final String transport = uri.getTransportParam();
            if ("udp".equalsIgnoreCase(transport)) {
                result = uri;
            }
        }
        return result;
    }

    private ActorRef session(final Configuration p_configuration, final Sid organizationSid) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new SmsSession(p_configuration, sipFactory, outboundInterface(), storage, monitoringService, servletContext, organizationSid);
            }
        });
        return getContext().actorOf(props);
    }

    public void outbound(SmppOutboundMessageEntity request) throws SmppInvalidArgumentException, IOException {
//        if(logger.isInfoEnabled()) {
//            logger.info("Message is Received by the SmppSessionOutbound Class");
//        }

        byte[] textBytes;
        int smppTonNpiValue = Integer.parseInt(SmppService.getSmppTonNpiValue());
        // add delivery receipt
        //submit0.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);
        SubmitSm submit0 = new SubmitSm();
        submit0.setSourceAddress(new Address((byte) smppTonNpiValue, (byte) smppTonNpiValue, request.getSmppFrom()));
        submit0.setDestAddress(new Address((byte) smppTonNpiValue, (byte) smppTonNpiValue, request.getSmppTo()));
        submit0.setDataCoding(request.getDataCodingScheme());
        textBytes = CharsetUtil.encode(request.getSmppContent(), request.getSmppEncoding());

        //TODO reverted from https://telestax.atlassian.net/browse/RESTCOMM-1595 as it caused SMS loop at SMSC
        //TODO the delivery receipt should be introduced only together with the remaining/pending DLR implementation
        //TODO the DLR implementation should be configurable (on/off)
        //TODO when enabling delivery receipts again, enable also SmppTest.testClientSentOutUsingSMPPDeliveryReceipt()
        //set the delivery flag to true
        //submit0.setRegisteredDelivery((byte) 1);

        submit0.setShortMessage(textBytes);

        TlvSet tlvSet = request.getTlvSet();

        if (tlvSet != null) {
            for (Tlv tlv : (Collection<Tlv>) tlvSet.getOptionalParameters()) {
                submit0.setOptionalParameter(tlv);
            }
        } else if (logger.isInfoEnabled()) {
            logger.info("TlvSet is null");
        }
        try {
            if (logger.isInfoEnabled()) {
                logger.info("Sending SubmitSM for " + request);
            }
            SmppClientOpsThread.getSmppSession().submit(submit0, 10000); //send message through SMPP connector
        } catch (RecoverablePduException | UnrecoverablePduException | SmppTimeoutException | SmppChannelException | InterruptedException e) {
            logger.error("SMPP message cannot be sent : " + e);
        }
    }
}

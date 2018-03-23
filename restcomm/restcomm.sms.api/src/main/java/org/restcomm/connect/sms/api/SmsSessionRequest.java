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
package org.restcomm.connect.sms.api;

import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.sip.SipServletRequest;
import org.restcomm.smpp.parameter.TlvSet;
import org.restcomm.connect.commons.annotations.concurrency.Immutable;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Immutable
public final class SmsSessionRequest {
    private final String from;
    private final String to;
    private final String body;
    private final byte dataCodingScheme;
    private final Encoding encoding;
    private final SipServletRequest origRequest;
    private final ConcurrentHashMap<String, String> customHeaders;
    private final TlvSet tlvSet;

    public enum Encoding {
        UCS_2("UCS-2"),
        UTF_8("UTF-8"),
        GSM("GSM"),
        GSM7("GSM7"),
        GSM8("GSM8"),
        ISO_8859_1("ISO-8859-1"),
        ISO_8859_15("ISO-8859-15");

        private final String name;

        Encoding(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }
    //FIXME: all signatures should be changed, encoding, sipReq, tlvSet are optional and should come after headers
    //(from, to, body, headers, encoding, sipReq, tlvSet)
    //(from, to, body, headers, encoding, sipReq)
    //(from, to, body, headers, encoding, tlvSet)
    //(from, to, body, headers, sipReq, tlvSet)
    //(from, to, body, headers, encoding)
    //(from, to, body, headers, sipReq)
    //(from, to, body, headers, tlvSet)
    //(from, to, body)

    /**
     * @deprecated Use {@link #SmsSessionRequest(String,String,String,byte,Encoding,SipServletRequest,TlvSet,ConcurrentHashMap<String, String>)} instead
     */
    public SmsSessionRequest(final String from, final String to, final String body, final Encoding encoding, final SipServletRequest origRequest, TlvSet tlvSet, final ConcurrentHashMap<String, String> customHeaders) {
        this(from, to, body, (byte)0, encoding, origRequest, tlvSet,
                customHeaders);
    }

    public SmsSessionRequest(final String from, final String to, final String body, byte dcs, final Encoding encoding, final SipServletRequest origRequest, TlvSet tlvSet, final ConcurrentHashMap<String, String> customHeaders) {
        super();
        this.from = from;
        this.to = to;
        this.origRequest = origRequest;
        this.body = body;
        this.customHeaders = customHeaders;
        this.dataCodingScheme = dcs;
        this.encoding = encoding;
        this.tlvSet = tlvSet;
    }

    //TODO need to check which is using the SmsSessionRequest and modify accordingly to include or not the custom headers
    public SmsSessionRequest(final String from, final String to, final String body, final Encoding encoding, final SipServletRequest origRequest, final ConcurrentHashMap<String, String> customHeaders) {
        this(from, to, body, (byte)0, encoding, origRequest, null, customHeaders);
    }

    public SmsSessionRequest(final String from, final String to, final String body, final Encoding encoding, final TlvSet tlvSet, final ConcurrentHashMap<String, String> customHeaders) {
        this(from, to, body, (byte)0, encoding, null, tlvSet, customHeaders);
    }

    public SmsSessionRequest(final String from, final String to, final String body, final SipServletRequest origRequest, final TlvSet tlvSet, final ConcurrentHashMap<String, String> customHeaders) {
        this(from, to, body, (byte)0, Encoding.UTF_8, origRequest, tlvSet, customHeaders);
    }

    public SmsSessionRequest(final String from, final String to, final String body, final SipServletRequest origRequest, final ConcurrentHashMap<String, String> customHeaders) {
        this(from, to, body, (byte)0, Encoding.UTF_8, origRequest, null, customHeaders);
    }

    public SmsSessionRequest(final String from, final String to, final String body, byte dcs,final Encoding encoding, final ConcurrentHashMap<String, String> customHeaders) {
        this(from, to, body, dcs, encoding, null, null, customHeaders);
    }

    public SmsSessionRequest(final String from, final String to, final String body, final TlvSet tlvSet, final ConcurrentHashMap<String, String> customHeaders) {
        this(from, to, body, (byte)0, Encoding.UTF_8, null, null, customHeaders);
    }

    public SmsSessionRequest(final String from, final String to, final String body, final ConcurrentHashMap<String, String> customHeaders) {
        this(from, to, body, (byte)0, Encoding.UTF_8, null, null, customHeaders);
    }

    public SmsSessionRequest(final String from, final String to, final String body) {
        this(from, to, body, (byte)0, Encoding.UTF_8, null, null, null);
    }

    public SmsSessionRequest(String smppFrom, String smppTo, String smppContent, byte smppDataCodingScheme, Encoding encoding, TlvSet tlvSet, final ConcurrentHashMap<String, String> customHeaders) {
        this(smppFrom, smppTo, smppContent, smppDataCodingScheme, encoding, null, tlvSet, customHeaders);
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public String body() {
        return body;
    }

    public Encoding encoding() {
        return encoding;
    }

    public byte getDataCodingScheme() {
        return dataCodingScheme;
    }

    public TlvSet getTlvSet() {
        return tlvSet;
    }

    public SipServletRequest getOrigRequest() {
        return origRequest;
    }

    public ConcurrentHashMap<String, String> headers() {
        return customHeaders;
    }
}

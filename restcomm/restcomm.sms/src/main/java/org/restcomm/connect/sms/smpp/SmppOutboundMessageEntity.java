package org.restcomm.connect.sms.smpp;

import org.restcomm.smpp.parameter.TlvSet;

import com.cloudhopper.commons.charset.Charset;

public class SmppOutboundMessageEntity {


    private final String smppTo;
    private final String smppFrom;
    private final String smppContent;
    private final Charset smppEncoding;
    private final byte dataCodingScheme;
    private final TlvSet tlvSet;


    public SmppOutboundMessageEntity(String smppTo, String smppFrom, String smppContent, byte dataCodingScheme, Charset smppEncoding){
         this(smppTo, smppFrom, smppContent, dataCodingScheme, smppEncoding, null);
    }

    public SmppOutboundMessageEntity(String smppTo, String smppFrom, String smppContent, byte dataCodingScheme, Charset smppEncoding, TlvSet tlvSet){
        this.smppTo = smppTo;
        this.smppFrom = smppFrom;
        this.smppContent = smppContent;
        this.smppEncoding = smppEncoding;
        this.dataCodingScheme = dataCodingScheme;
        this.tlvSet = tlvSet;
    }

    public final TlvSet getTlvSet(){
        return tlvSet;
    }

    public final String getSmppTo(){
        return smppTo;
    }

    public final String getSmppFrom(){
        return smppFrom;
    }

    public final String getSmppContent(){
        return smppContent;
    }

    public final Charset getSmppEncoding(){
        return smppEncoding;
    }

    public byte getDataCodingScheme() {
        return dataCodingScheme;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("SMPPOutboundMessage[From=")
            .append(smppFrom)
            .append(",To")
            .append(smppTo)
            .append(",Content=")
            .append(smppContent)
            .append(",Encoding=")
            .append(smppEncoding)
            .append(",DataCoding=")
            .append(dataCodingScheme);
        if(tlvSet!=null){
        builder.append(",TlvSet=")
            .append(tlvSet.toString());
        }

        return super.toString();
    }
}

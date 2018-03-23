package org.restcomm.connect.sms.smpp;

import org.restcomm.smpp.parameter.TlvSet;

import com.cloudhopper.commons.charset.Charset;

public class SmppInboundMessageEntity {

    private final String smppTo;
    private final String smppFrom;
    private final byte[] smppRawContent;
    private final byte smppDataCodingScheme;
    private final Charset smppEncoding;
    private final TlvSet tlvSet;
    private final boolean isDeliveryReceipt;

    public SmppInboundMessageEntity(String smppTo, String smppFrom, byte[] smppRawContent, byte smppDataCodingScheme, Charset smppEncoding){
        this(smppTo, smppFrom, smppRawContent, smppDataCodingScheme, smppEncoding, null, false);
    }

    public SmppInboundMessageEntity(String smppTo, String smppFrom, byte[] smppRawContent, byte smppDataCodingScheme, Charset smppEncoding, TlvSet tlvSet, boolean isDeliveryReceipt) {
        this.smppTo = smppTo;
        this.smppFrom = smppFrom;
        this.smppRawContent = smppRawContent;
        this.smppDataCodingScheme = smppDataCodingScheme;
        this.smppEncoding = smppEncoding;
        this.tlvSet = tlvSet;
        this.isDeliveryReceipt = isDeliveryReceipt;
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

    public String getSmppContent() {
        return smppEncoding.decode(smppRawContent);
    }

    public byte[] getSmppRawContent() {
        return smppRawContent;
    }

    public byte getSmppDataCodingScheme() {
        return smppDataCodingScheme;
    }

    public final Charset getSmppEncoding(){
        return smppEncoding;
    }

    public final boolean getIsDeliveryReceipt() {
        return isDeliveryReceipt;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("SMPPInboundMessage[From=")
            .append(smppFrom)
            .append(",To")
            .append(smppTo)
            .append(",RawContent=")
            .append(smppRawContent)
            .append(",Encoding=")
            .append(smppEncoding)
            .append("isDeliveryReceipt=")
            .append(isDeliveryReceipt);

        return builder.toString();
    }
}

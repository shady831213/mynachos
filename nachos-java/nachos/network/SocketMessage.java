package nachos.network;

import nachos.machine.Lib;
import nachos.machine.MalformedPacketException;

public class SocketMessage {

    public SocketMessage() {
        new SocketMessage(false, false, false, false, 0);
    }

    public SocketMessage(boolean fin, boolean stp, boolean ack, boolean syn, int seqNo) {
        this.fin = fin;
        this.stp = stp;
        this.ack = ack;
        this.syn = syn;
        this.seqNo = seqNo;
    }

    public SocketMessage(boolean fin, boolean stp, boolean ack, boolean syn, int seqNo, byte[] contents) throws MalformedPacketException {
        new SocketMessage(fin, stp, ack, syn, seqNo);
        if (contents.length > maxContentsLength)
            throw new MalformedPacketException();
        this.contents = contents;
    }

    public void sendMailMessage(MailMessage message) throws MalformedPacketException {
        this.message = new MailMessage(message.packet.dstLink, message.dstPort, message.packet.srcLink, message.srcPort, packContent());
    }

    private byte[] packContent() {

        byte[] mailContents = new byte[headerLength + contents.length];
        mailContents[0] = 0;
        mailContents[1] = packCtrlBits();
        System.arraycopy(Lib.bytesFromInt(this.seqNo), 0, mailContents, 2,
                4);
        System.arraycopy(contents, 0, mailContents, headerLength,
                contents.length);

        return mailContents;
    }

    public void recMailMessage(MailMessage message) throws MalformedPacketException {
        this.message = message;

        // make sure we have a valid header
        if (message.contents.length < headerLength)
            throw new MalformedPacketException();

        upPackCtrlBits(message.contents[1]);
        seqNo = Lib.bytesToInt(message.contents, 2, 4);

        contents = new byte[message.contents.length - headerLength];
        System.arraycopy(message.contents, headerLength, contents, 0,
                contents.length);
    }


    public byte packCtrlBits() {
        return (byte) (bool2Bit(syn) << 3 | bool2Bit(ack) << 2 | bool2Bit(stp) << 1 | bool2Bit(fin));
    }

    public void upPackCtrlBits(byte ctrlBits) {
        fin = bit2Bool(getBit(ctrlBits, 0));
        stp = bit2Bool(getBit(ctrlBits, 1));
        ack = bit2Bool(getBit(ctrlBits, 2));
        syn = bit2Bool(getBit(ctrlBits, 3));
    }

    public boolean isData() {
        return !fin && !stp && !syn;
    }

    private byte bool2Bit(boolean var) {
        if (var) {
            return 1;
        }
        return 0;
    }

    private boolean bit2Bool(byte var) {
        return var != 0;
    }

    private byte getBit(byte var, int bitSel) {
        return (byte) (var & (0x1 << bitSel));
    }

    public boolean fin;
    public boolean stp;
    public boolean ack;
    public boolean syn;

    public int seqNo;

    public MailMessage message;

    public byte[] contents;

    public static final int headerLength = 6;

    public static final int maxContentsLength =
            MailMessage.maxContentsLength - headerLength;
}
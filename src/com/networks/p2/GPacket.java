package com.networks.p2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.BufferUnderflowException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;


public class GPacket {
    public static final byte VERSION_NUM = 1;
    public static final byte TYPE_QUESTION   = 0x01;
    public static final byte TYPE_NEXT       = 0x02;
    public static final byte TYPE_BUZZ       = 0x03;
    public static final byte TYPE_BUZZ_RES   = 0x04;
    public static final byte TYPE_ANSWER     = 0x05;
    public static final byte TYPE_ANSWER_RES = 0x06;
    public static final byte TYPE_KILL       = 0x07;
    public static final byte TYPE_SCORE      = 0x08;
    public static final byte TYPE_REJOIN     = 0x09;

    private byte version;
    private byte type;
    private short nodeID;
    private long timestamp;
    private int length;
    private byte[] data;

    public GPacket(byte type, short nodeID, long timestamp, byte[] data) {
        this.version = VERSION_NUM;
        this.type = type;
        this.nodeID = nodeID;
        this.timestamp = timestamp;
        this.length = (data == null) ? 0 : data.length;
        this.data = (data == null) ? new byte[0] : data;
    }

    public byte[] convertToBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(16 + length);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put(version);
        buffer.put(type);
        buffer.putShort(nodeID);
        buffer.putInt((int) (timestamp >> 32));
        buffer.putInt((int) timestamp);
        buffer.putInt(length);

        if (length > 0) {
            buffer.put(data);
        } else {
            System.out.println("Warning: Empty packet being sent.");
        }

        return buffer.array();
    }

    public static GPacket convertFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 16) {
            System.out.println("Error: Received an incomplete or empty packet (size: " + (bytes == null ? 0 : bytes.length) + "). Ignoring.");
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        try {
            byte version = buffer.get();
            byte type = buffer.get();
            short nodeID = buffer.getShort();
            long timeHigh = buffer.getInt() & 0xFFFFFFFFL;
            long timeLow = buffer.getInt() & 0xFFFFFFFFL;
            long timestamp = (timeHigh << 32) | timeLow;
            int length = buffer.getInt();

            if (length < 0 || length > bytes.length - 16) {
                System.out.println("Error: Malformed packet with invalid data length (" + length + "). Ignoring.");
                return null;
            }

            byte[] data = new byte[length];
            if (length > 0) {
                buffer.get(data);
            } else {
                System.out.println("Warning: Received an empty data payload.");
            }

            return new GPacket(type, nodeID, timestamp, data);

        } catch (BufferUnderflowException e) {
            System.out.println("Critical Error: Packet parsing failed due to missing bytes. Packet might be corrupted.");
            e.printStackTrace();
            return null;
        }
    }

    public static GPacket tcpRead(InputStream in) throws IOException {
        // Step 1: Read fixed-size header (16 bytes)
        byte[] header = new byte[16];
        int totalRead = 0;
        while (totalRead < 16) {
            int bytesRead = in.read(header, totalRead, 16 - totalRead);
            if (bytesRead == -1) throw new EOFException("Stream closed during header read.");
            totalRead += bytesRead;
        }

        // Step 2: Extract data length (last 4 bytes of header)
        ByteBuffer headerBuffer = ByteBuffer.wrap(header);
        headerBuffer.order(ByteOrder.BIG_ENDIAN); // match protocol's endianness
        headerBuffer.position(12); // jump to the 'length' field
        int length = headerBuffer.getInt();

        // Validate length (optional safety check)
        if (length < 0 || length > 10_000_000) {
            throw new IOException("Suspicious packet length: " + length);
        }

        // Step 3: Read 'length' bytes of data
        byte[] data = new byte[length];
        totalRead = 0;
        while (totalRead < length) {
            int bytesRead = in.read(data, totalRead, length - totalRead);
            if (bytesRead == -1) throw new EOFException("Stream closed during data read.");
            totalRead += bytesRead;
        }

        // Step 4: Combine header + data into one packet
        ByteArrayOutputStream fullPacket = new ByteArrayOutputStream();
        fullPacket.write(header);
        fullPacket.write(data);
        return convertFromBytes(fullPacket.toByteArray());
    }

    public String printInfo() {
        return "Protocol{" +
                "version = " + version +
                ", type=" + type +
                ", nodeID=" + nodeID +
                ", timestamp=" + timestamp +
                ", length=" + length +
                ", data=" + (data.length > 0 ? new String(data) : "No Data") +
                '}';
    }

    public byte getVersion() {
        return version;
    }
    public byte getType() {
        return type;
    }
    public short getNodeID() {
        return nodeID;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public int getLength() {
        return length;
    }
    public byte[] getData() {
        return data;
    }

}

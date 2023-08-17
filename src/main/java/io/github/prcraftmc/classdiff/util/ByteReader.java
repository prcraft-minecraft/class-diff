package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.ByteVector;

/**
 * The inverse of {@link ByteVector}
 */
public class ByteReader {
    private final byte[] data;
    private int ptr;

    public ByteReader(byte[] data) {
        this.data = data;
    }

    public ByteReader(byte[] data, int ptr) {
        this.data = data;
        this.ptr = ptr;
    }

    public int pointer() {
        return ptr;
    }

    public int readByte() {
        return data[ptr++] & 0xff;
    }

    public int readShort() {
        return ((data[ptr++] & 0xff) << 8) | (data[ptr++] & 0xff);
    }

    public int readInt() {
        final int offset = ptr;
        ptr += 4;
        return ((data[offset] & 0xff) << 24)
            | ((data[offset + 1] & 0xff) << 16)
            | ((data[offset + 2] & 0xff) << 8)
            | (data[offset + 3] & 0xff);
    }

    public long readLong() {
        return ((readInt() & 0xffffffffL) << 32) | (readInt() & 0xffffffffL);
    }

    public String readUtf8() {
        final int length = readShort();
        final char[] result = new char[length];

        int offset = ptr;
        final int endOffset = offset + length;
        int strLength = 0;
        final byte[] input = data;
        while (offset < endOffset) {
            final int currentByte = input[offset++];
            if ((currentByte & 0x80) == 0) {
                result[strLength++] = (char)(currentByte & 0x7f);
            } else if ((currentByte & 0xE0) == 0xC0) {
                result[strLength++] = (char)(((currentByte & 0x1f) << 6) + (input[offset++] & 0x3f));
            } else {
                result[strLength++] =
                    (char)(((currentByte & 0xf) << 12)
                        + ((input[offset++] & 0x3f) << 6)
                        + (input[offset++] & 0x3f)
                    );
            }
        }
        ptr = offset;

        return new String(result);
    }

    public byte[] readByteArray(byte[] byteArray, int offset, int length) {
        System.arraycopy(data, ptr, byteArray, offset, length);
        ptr += length;
        return byteArray;
    }

    public byte[] readByteArray(byte[] byteArray) {
        return readByteArray(byteArray, 0, byteArray.length);
    }
}

package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.util.ByteReader;
import io.github.prcraftmc.classdiff.util.PatchReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import java.util.Arrays;
import java.util.Collections;

public class DiffReader {
    private final PatchReader<String> classPatchReader = new PatchReader<>(reader -> {
        reader.readShort();
        return readClass(reader.pointer() - 2);
    });

    private final byte[] contents;

    private int version;
    private int[] constantOffsets;
    private String[] constantStringCache;
    private char[] charBuffer;
    private int startPos;

    public DiffReader(byte[] contents) {
        this.contents = contents;
        readStart();
    }

    private void readStart() {
        if (readInt(0) != DiffConstants.MAGIC) {
            throw new IllegalArgumentException("Class diff did not start with magic 0xEBABEFAC");
        }
        version = readShort(4);
        if (version < DiffConstants.V1 || version > DiffConstants.V_MAX) {
            throw new IllegalArgumentException(
                "Unsupported class diff version. Read " + version +
                ". Class diff only supports 1 through " + DiffConstants.V_MAX + "."
            );
        }

        final int constantCount = readShort(6);
        constantOffsets = new int[constantCount + 1];
        constantStringCache = new String[constantCount + 1];

        int maxStringSize = 0;
        int pointer = 8;
        for (int i = 1; i < constantCount; i++) {
            constantOffsets[i] = pointer + 1;
            int size;
            switch (contents[pointer]) {
                case Symbol.CONSTANT_FIELDREF_TAG:
                case Symbol.CONSTANT_METHODREF_TAG:
                case Symbol.CONSTANT_INTERFACE_METHODREF_TAG:
                case Symbol.CONSTANT_INTEGER_TAG:
                case Symbol.CONSTANT_FLOAT_TAG:
                case Symbol.CONSTANT_NAME_AND_TYPE_TAG:
                case Symbol.CONSTANT_DYNAMIC_TAG:
                case Symbol.CONSTANT_INVOKE_DYNAMIC_TAG:
                    size = 5;
                    break;
                case Symbol.CONSTANT_LONG_TAG:
                case Symbol.CONSTANT_DOUBLE_TAG:
                    size = 9;
                    break;
                case Symbol.CONSTANT_UTF8_TAG:
                    size = 3 + readShort(pointer + 1);
                    if (size > maxStringSize) {
                        maxStringSize = size;
                    }
                    break;
                case Symbol.CONSTANT_METHOD_HANDLE_TAG:
                    size = 4;
                    break;
                case Symbol.CONSTANT_CLASS_TAG:
                case Symbol.CONSTANT_STRING_TAG:
                case Symbol.CONSTANT_METHOD_TYPE_TAG:
                case Symbol.CONSTANT_PACKAGE_TAG:
                case Symbol.CONSTANT_MODULE_TAG:
                    size = 3;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown constant type: " + contents[pointer]);
            }
            pointer += size;
        }
        charBuffer = new char[maxStringSize];

        startPos = pointer;
    }

    public void accept(DiffVisitor visitor, ClassNode context) {
        int readPos;
        final Patch<String> interfacePatch;
        {
            if (readShort(startPos + 14) == 0) {
                interfacePatch = null;
                readPos = startPos + 16;
            } else {
                final ByteReader byteReader = new ByteReader(contents, startPos + 14);
                interfacePatch = classPatchReader.readPatch(
                    byteReader, context.interfaces != null ? context.interfaces : Collections.emptyList()
                );
                readPos = byteReader.pointer();
            }
        }

        visitor.visit(
            version,
            readInt(startPos),
            readInt(startPos + 4),
            readClass(startPos + 8),
            readUtf8(startPos + 10),
            readClass(startPos + 12),
            interfacePatch
        );

        final int attributeCount = readShort(readPos);
        readPos += 2;
        for (int i = 0; i < attributeCount; i++) {
            final String attributeName = readUtf8(readPos);
            if (attributeName == null) {
                throw new IllegalArgumentException("Null attribute name at address " + Integer.toHexString(readPos));
            }
            final int attributeLength = readInt(readPos + 2);
            readPos += 6;
            switch (attributeName) {
                case "Source":
                    visitor.visitSource(readUtf8(readPos), readUtf8(readPos + 2));
                    break;
                case "InnerClasses":
                    visitor.visitInnerClasses(new PatchReader<>(reader -> {
                        reader.readInt();
                        reader.readShort();
                        return new InnerClassNode(
                            readClass(reader.pointer() - 6),
                            readClass(reader.pointer() - 4),
                            readUtf8(reader.pointer() - 2),
                            reader.readShort()
                        );
                    }).readPatch(new ByteReader(contents, readPos), context.innerClasses));
                    break;
                default:
                    if (attributeName.startsWith("Custom")) {
                        if (contents[readPos] != 0) {
                            visitor.visitCustomAttribute(
                                attributeName.substring(6),
                                Arrays.copyOfRange(contents, readPos + 1, readPos + attributeLength)
                            );
                        } else {
                            visitor.visitCustomAttribute(attributeName.substring(6), null);
                        }
                    }
                    break;
            }
            readPos += attributeLength;
        }
    }

    private int readInt(int offset) {
        return ((contents[offset] & 0xff) << 24)
            | ((contents[offset + 1] & 0xff) << 16)
            | ((contents[offset + 2] & 0xff) << 8)
            | (contents[offset + 3] & 0xff);
    }

    private int readShort(int offset) {
        return ((contents[offset] & 0xff) << 8)
            | (contents[offset + 1] & 0xff);
    }

    private String readClass(int offset) {
        return readStringish(offset);
    }

    private String readStringish(int offset) {
        return readUtf8(constantOffsets[readShort(offset)]);
    }

    private String readUtf8(int offset) {
        final int constantIndex = readShort(offset);
        if (offset == 0 || constantIndex == 0) {
            return null;
        }
        return readUtf(constantIndex);
    }

    private String readUtf(int constantIndex) {
        final String result = constantStringCache[constantIndex];
        if (result != null) {
            return result;
        }
        final int offset = constantOffsets[constantIndex];
        return constantStringCache[constantIndex] = readUtf(offset + 2, readShort(offset));
    }

    private String readUtf(int utfOffset, int utfLength) {
        final char[] charBuffer = this.charBuffer;
        int currentOffset = utfOffset;
        final int endOffset = currentOffset + utfLength;
        int strLength = 0;
        final byte[] input = contents;
        while (currentOffset < endOffset) {
            final int currentByte = input[currentOffset++];
            if ((currentByte & 0x80) == 0) {
                charBuffer[strLength++] = (char)(currentByte & 0x7f);
            } else if ((currentByte & 0xE0) == 0xC0) {
                charBuffer[strLength++] = (char)(((currentByte & 0x1f) << 6) + (input[currentOffset++] & 0x3f));
            } else {
                charBuffer[strLength++] =
                    (char)(((currentByte & 0xf) << 12)
                        + ((input[currentOffset++] & 0x3f) << 6)
                        + (input[currentOffset++] & 0x3f)
                    );
            }
        }
        return new String(charBuffer, 0, strLength);
    }
}

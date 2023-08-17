package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.util.ByteReader;
import io.github.prcraftmc.classdiff.util.PatchReader;
import io.github.prcraftmc.classdiff.util.ReflectUtils;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import java.util.Arrays;
import java.util.Collections;

public class DiffReader {
    private final PatchReader<String> classPatchReader = new PatchReader<>(reader -> {
        reader.skip(2);
        return readClass(reader.pointer() - 2);
    });
    private final PatchReader<AnnotationNode> annotationPatchReader = new PatchReader<>(reader -> {
        final AnnotationNode result = new AnnotationNode(readClass(reader.pointer()));
        reader.pointer(readElementValues(result, reader.pointer() + 2, true));
        return result;
    });
    private final PatchReader<TypeAnnotationNode> typeAnnotationPatchReader = new PatchReader<>(reader -> {
        final Context context = this.context.get();
        reader.pointer(readTypeAnnotationTarget(reader.pointer(), context));
        final TypeAnnotationNode result = new TypeAnnotationNode(
            context.currentTypeAnnotationTarget,
            context.currentTypeAnnotationTargetPath,
            readClass(reader.pointer())
        );
        reader.pointer(readElementValues(result, reader.pointer() + 2, true));
        return result;
    });

    private final byte[] contents;

    private int version;
    private int[] constantOffsets;
    private String[] constantStringCache;
    private char[] charBuffer;
    private int startPos;

    private ConstantDynamic[] condyCache;
    private int[] bsmOffsets;

    private final ThreadLocal<Context> context = new ThreadLocal<>();

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
        boolean hasCondy = false;
        boolean hasBsm = false;
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
                    size = 5;
                    break;
                case Symbol.CONSTANT_DYNAMIC_TAG:
                    size = 5;
                    hasBsm = true;
                    hasCondy = true;
                    break;
                case Symbol.CONSTANT_INVOKE_DYNAMIC_TAG:
                    size = 5;
                    hasBsm = true;
                    break;
                case Symbol.CONSTANT_LONG_TAG:
                case Symbol.CONSTANT_DOUBLE_TAG:
                    size = 9;
                    i++;
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

        if (hasCondy) {
            condyCache = new ConstantDynamic[constantCount];
        }
        if (hasBsm) {
            bsmOffsets = readBsmAttribute();
        }
    }

    public void accept(DiffVisitor visitor, ClassNode node) {
        context.set(new Context());

        int readPos;
        final Patch<String> interfacePatch;
        {
            if (readShort(startPos + 14) == 0) {
                interfacePatch = null;
                readPos = startPos + 16;
            } else {
                final ByteReader byteReader = new ByteReader(contents, startPos + 14);
                interfacePatch = classPatchReader.readPatch(
                    byteReader, node.interfaces != null ? node.interfaces : Collections.emptyList()
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
                        reader.skip(6);
                        return new InnerClassNode(
                            readClass(reader.pointer() - 6),
                            readClass(reader.pointer() - 4),
                            readUtf8(reader.pointer() - 2),
                            reader.readShort()
                        );
                    }).readPatch(
                        new ByteReader(contents, readPos),
                        node.innerClasses != null ? node.innerClasses : Collections.emptyList()
                    ));
                    break;
                case "OuterClasses":
                    visitor.visitOuterClass(readClass(readPos), readClass(readPos + 2), readClass(readPos + 4));
                    break;
                case "NestHost":
                    visitor.visitNestHost(readClass(readPos));
                    break;
                case "NestMembers":
                    visitor.visitNestMembers(classPatchReader.readPatch(
                        new ByteReader(contents, readPos),
                        node.nestMembers != null ? node.nestMembers : Collections.emptyList()
                    ));
                    break;
                case "PermittedSubclasses":
                    visitor.visitPermittedSubclasses(classPatchReader.readPatch(
                        new ByteReader(contents, readPos),
                        node.permittedSubclasses != null ? node.permittedSubclasses : Collections.emptyList()
                    ));
                    break;
                case "VisibleAnnotations":
                    visitor.visitAnnotations(annotationPatchReader.readPatch(
                        new ByteReader(contents, readPos),
                        node.visibleAnnotations != null ? node.visibleAnnotations : Collections.emptyList()
                    ), true);
                    break;
                case "InvisibleAnnotations":
                    visitor.visitAnnotations(annotationPatchReader.readPatch(
                        new ByteReader(contents, readPos),
                        node.invisibleAnnotations != null ? node.invisibleAnnotations : Collections.emptyList()
                    ), false);
                    break;
                case "VisibleTypeAnnotations":
                    visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                        new ByteReader(contents, readPos),
                        node.visibleTypeAnnotations != null ? node.visibleTypeAnnotations : Collections.emptyList()
                    ), true);
                    break;
                case "InvisibleTypeAnnotations":
                    visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                        new ByteReader(contents, readPos),
                        node.invisibleTypeAnnotations != null ? node.invisibleTypeAnnotations : Collections.emptyList()
                    ), false);
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

        context.remove();
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

    private int readElementValues(AnnotationVisitor annotationVisitor, int currentOffset, boolean named) {
        int numElementValuePairs = readShort(currentOffset);
        currentOffset += 2;
        if (named) {
            while (numElementValuePairs-- > 0) {
                final String elementName = readUtf8(currentOffset);
                currentOffset = readElementValue(annotationVisitor, currentOffset + 2, elementName);
            }
        } else {
            while (numElementValuePairs-- > 0) {
                currentOffset = readElementValue(annotationVisitor, currentOffset, null);
            }
        }
        annotationVisitor.visitEnd();
        return currentOffset;
    }

    private int readElementValue(AnnotationVisitor annotationVisitor, int currentOffset, String elementName) {
        switch (contents[currentOffset++] & 0xff) {
            case 'B':
                annotationVisitor.visit(elementName, (byte)readInt(constantOffsets[readShort(currentOffset)]));
                currentOffset += 2;
                break;
            case 'C':
                annotationVisitor.visit(elementName, (char)readInt(constantOffsets[readShort(currentOffset)]));
                currentOffset += 2;
                break;
            case 'D':
            case 'F':
            case 'I':
            case 'J':
                annotationVisitor.visit(elementName, readConst(readShort(currentOffset)));
                currentOffset += 2;
                break;
            case 'S':
                annotationVisitor.visit(elementName, (short)readInt(constantOffsets[readShort(currentOffset)]));
                currentOffset += 2;
                break;
            case 'Z':
                annotationVisitor.visit(elementName, readInt(constantOffsets[readShort(currentOffset)]) != 0);
                currentOffset += 2;
                break;
            case 's':
                annotationVisitor.visit(elementName, readUtf8(currentOffset));
                currentOffset += 2;
                break;
            case 'e':
                annotationVisitor.visitEnum(elementName, readUtf8(currentOffset), readUtf8(currentOffset + 2));
                currentOffset += 4;
                break;
            case 'c':
                annotationVisitor.visit(elementName, Type.getType(readUtf8(currentOffset)));
                currentOffset += 2;
                break;
            case '@':
                currentOffset = readElementValues(
                    annotationVisitor.visitAnnotation(elementName, readUtf8(currentOffset)),
                    currentOffset + 2,
                    true
                );
                break;
            case '[': {
                final int numValues = readShort(currentOffset);
                currentOffset += 2;
                if (numValues == 0) {
                    return readElementValues(annotationVisitor.visitArray(elementName), currentOffset - 2, false);
                }
                switch (contents[currentOffset] & 0xff) {
                    case 'B': {
                        final byte[] values = new byte[numValues];
                        for (int i = 0; i < numValues; i++) {
                            values[i] = (byte)readInt(constantOffsets[readShort(currentOffset + 1)]);
                            currentOffset += 3;
                        }
                        annotationVisitor.visit(elementName, values);
                        break;
                    }
                    case 'Z': {
                        final boolean[] values = new boolean[numValues];
                        for (int i = 0; i < numValues; i++) {
                            values[i] = readInt(constantOffsets[readShort(currentOffset + 2)]) != 0;
                            currentOffset += 3;
                        }
                        annotationVisitor.visit(elementName, values);
                        break;
                    }
                    case 'S': {
                        final short[] values = new short[numValues];
                        for (int i = 0; i < numValues; i++) {
                            values[i] = (short)readInt(constantOffsets[readShort(currentOffset + 1)]);
                            currentOffset += 3;
                        }
                        annotationVisitor.visit(elementName, values);
                        break;
                    }
                    case 'C': {
                        final char[] values = new char[numValues];
                        for (int i = 0; i < numValues; i++) {
                            values[i] = (char)readInt(constantOffsets[readShort(currentOffset + 1)]);
                            currentOffset += 3;
                        }
                        annotationVisitor.visit(elementName, values);
                        break;
                    }
                    case 'I': {
                        final int[] values = new int[numValues];
                        for (int i = 0; i < numValues; i++) {
                            values[i] = readInt(constantOffsets[readShort(currentOffset + 1)]);
                            currentOffset += 3;
                        }
                        annotationVisitor.visit(elementName, values);
                        break;
                    }
                    case 'J': {
                        final long[] values = new long[numValues];
                        for (int i = 0; i < numValues; i++) {
                            values[i] = readLong(constantOffsets[readShort(currentOffset + 1)]);
                            currentOffset += 3;
                        }
                        annotationVisitor.visit(elementName, values);
                        break;
                    }
                    case 'F': {
                        final float[] values = new float[numValues];
                        for (int i = 0; i < numValues; i++) {
                            values[i] = Float.intBitsToFloat(readInt(constantOffsets[readShort(currentOffset + 1)]));
                            currentOffset += 3;
                        }
                        annotationVisitor.visit(elementName, values);
                        break;
                    }
                    case 'D': {
                        final double[] values = new double[numValues];
                        for (int i = 0; i < numValues; i++) {
                            values[i] = Double.longBitsToDouble(readLong(constantOffsets[readShort(currentOffset + 1)]));
                            currentOffset += 3;
                        }
                        annotationVisitor.visit(elementName, values);
                        break;
                    }
                    default:
                        currentOffset = readElementValues(annotationVisitor.visitArray(elementName), currentOffset - 2, false);
                        break;
                }
                break;
            }
            default:
                throw new IllegalArgumentException();
        }
        return currentOffset;
    }

    private Object readConst(int constantPoolEntryIndex) {
        final int cpInfoOffset = constantOffsets[constantPoolEntryIndex];
        switch (contents[cpInfoOffset - 1]) {
            case Symbol.CONSTANT_INTEGER_TAG:
                return readInt(cpInfoOffset);
            case Symbol.CONSTANT_FLOAT_TAG:
                return Float.intBitsToFloat(readInt(cpInfoOffset));
            case Symbol.CONSTANT_LONG_TAG:
                return readLong(cpInfoOffset);
            case Symbol.CONSTANT_DOUBLE_TAG:
                return Double.longBitsToDouble(readLong(cpInfoOffset));
            case Symbol.CONSTANT_CLASS_TAG:
                return Type.getObjectType(readUtf8(cpInfoOffset));
            case Symbol.CONSTANT_STRING_TAG:
                return readUtf8(cpInfoOffset);
            case Symbol.CONSTANT_METHOD_TYPE_TAG:
                return Type.getMethodType(readUtf8(cpInfoOffset));
            case Symbol.CONSTANT_METHOD_HANDLE_TAG: {
                final int referenceKind = contents[cpInfoOffset] & 0xff;
                final int referenceCpInfoOffset = constantOffsets[readShort(cpInfoOffset + 1)];
                final int nameAndTypeCpInfoOffset = constantOffsets[readShort(referenceCpInfoOffset + 2)];
                final String owner = readClass(referenceCpInfoOffset);
                final String name = readUtf8(nameAndTypeCpInfoOffset);
                final String descriptor = readUtf8(nameAndTypeCpInfoOffset + 2);
                final boolean isInterface = contents[referenceCpInfoOffset - 1] == Symbol.CONSTANT_INTERFACE_METHODREF_TAG;
                return new Handle(referenceKind, owner, name, descriptor, isInterface);
            }
            case Symbol.CONSTANT_DYNAMIC_TAG:
                return readConstantDynamic(constantPoolEntryIndex);
            default:
                throw new IllegalArgumentException();
        }
    }

    private long readLong(int offset) {
        return ((readInt(offset) & 0xffffffffL) << 32) | (readInt(offset + 4) & 0xffffffffL);
    }

    private int getFirstAttributeOffset() {
        final int deltaCount = readShort(startPos + 14);
        int offset = startPos + 16;
        for (int i = 0; i < deltaCount; i++) {
            switch (DeltaType.values()[contents[offset]]) {
                case CHANGE:
                    offset += 6 + 2 * readShort(offset + 4);
                    break;
                case DELETE:
                    offset += 4;
                    break;
                case INSERT:
                    offset += 4 + 2 * readShort(offset + 2);
                    break;
                case EQUAL:
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
        return offset + 2;
    }

    private int[] readBsmAttribute() {
        int currentAttributeOffset = getFirstAttributeOffset();
        for (int i = readShort(currentAttributeOffset - 2); i > 0; i--) {
            final String attrName = readUtf8(currentAttributeOffset);
            final int attrLength = readInt(currentAttributeOffset + 2);
            currentAttributeOffset += 6;
            if ("BootstrapMethods".equals(attrName)) {
                final int[] result = new int[readShort(currentAttributeOffset)];
                int currentBsmOffset = currentAttributeOffset + 2;
                for (int j = 0; j < result.length; j++) {
                    result[j] = currentBsmOffset;
                    currentBsmOffset += 4 + 2 * readShort(currentBsmOffset + 2);
                }
                return result;
            }
            currentAttributeOffset += attrLength;
        }
        throw new IllegalArgumentException();
    }

    private ConstantDynamic readConstantDynamic(int constantPoolEntryIndex) {
        final ConstantDynamic result = condyCache[constantPoolEntryIndex];
        if (result != null) {
            return result;
        }
        final int cpInfoOffset = constantOffsets[constantPoolEntryIndex];
        final int nameAndTypeCpInfoOffset = constantOffsets[readShort(cpInfoOffset + 2)];
        final String name = readUtf8(nameAndTypeCpInfoOffset);
        final String descriptor = readUtf8(nameAndTypeCpInfoOffset + 2);
        int bootstrapMethodOffset = bsmOffsets[readShort(cpInfoOffset)];
        final Handle handle = (Handle)readConst(readShort(bootstrapMethodOffset));
        final Object[] bootstrapMethodArguments = new Object[readShort(bootstrapMethodOffset + 2)];
        bootstrapMethodOffset += 4;
        for (int i = 0; i < bootstrapMethodArguments.length; i++) {
            bootstrapMethodArguments[i] = readConst(readShort(bootstrapMethodOffset));
            bootstrapMethodOffset += 2;
        }
        return condyCache[constantPoolEntryIndex] = new ConstantDynamic(name, descriptor, handle, bootstrapMethodArguments);
    }

    private int readTypeAnnotationTarget(int typeAnnotationOffset, Context context) {
        int currentOffset = typeAnnotationOffset;
        // Parse and store the target_type structure.
        int targetType = readInt(typeAnnotationOffset);
        switch (targetType >>> 24) {
            case TypeReference.CLASS_TYPE_PARAMETER:
            case TypeReference.METHOD_TYPE_PARAMETER:
            case TypeReference.METHOD_FORMAL_PARAMETER:
                targetType &= 0xFFFF0000;
                currentOffset += 2;
                break;
            case TypeReference.FIELD:
            case TypeReference.METHOD_RETURN:
            case TypeReference.METHOD_RECEIVER:
                targetType &= 0xFF000000;
                currentOffset += 1;
                break;
            case TypeReference.LOCAL_VARIABLE:
            case TypeReference.RESOURCE_VARIABLE:
                targetType &= 0xFF000000;
                int tableLength = readShort(currentOffset + 1);
                currentOffset += 3;
                context.currentLocalVariableAnnotationRangeStarts = new Label[tableLength];
                context.currentLocalVariableAnnotationRangeEnds = new Label[tableLength];
                context.currentLocalVariableAnnotationRangeIndices = new int[tableLength];
                for (int i = 0; i < tableLength; ++i) {
                    int startPc = readShort(currentOffset);
                    int length = readShort(currentOffset + 2);
                    int index = readShort(currentOffset + 4);
                    currentOffset += 6;
                    context.currentLocalVariableAnnotationRangeStarts[i] =
                        createLabel(startPc, context.currentMethodLabels);
                    context.currentLocalVariableAnnotationRangeEnds[i] =
                        createLabel(startPc + length, context.currentMethodLabels);
                    context.currentLocalVariableAnnotationRangeIndices[i] = index;
                }
                break;
            case TypeReference.CAST:
            case TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
            case TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT:
            case TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
            case TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT:
                targetType &= 0xFF0000FF;
                currentOffset += 4;
                break;
            case TypeReference.CLASS_EXTENDS:
            case TypeReference.CLASS_TYPE_PARAMETER_BOUND:
            case TypeReference.METHOD_TYPE_PARAMETER_BOUND:
            case TypeReference.THROWS:
            case TypeReference.EXCEPTION_PARAMETER:
                targetType &= 0xFFFFFF00;
                currentOffset += 3;
                break;
            case TypeReference.INSTANCEOF:
            case TypeReference.NEW:
            case TypeReference.CONSTRUCTOR_REFERENCE:
            case TypeReference.METHOD_REFERENCE:
                targetType &= 0xFF000000;
                currentOffset += 3;
                break;
            default:
                throw new IllegalArgumentException();
        }
        context.currentTypeAnnotationTarget = targetType;
        // Parse and store the target_path structure.
        int pathLength = contents[currentOffset] & 0xff;
        context.currentTypeAnnotationTargetPath =
            pathLength == 0 ? null : ReflectUtils.newTypePath(contents, currentOffset);
        // Return the start offset of the rest of the type_annotation structure.
        return currentOffset + 1 + 2 * pathLength;
    }

    private Label readLabel(int bytecodeOffset, Label[] labels) {
        if (labels[bytecodeOffset] == null) {
            labels[bytecodeOffset] = new Label();
        }
        return labels[bytecodeOffset];
    }

    private Label createLabel(int bytecodeOffset, Label[] labels) {
        return readLabel(bytecodeOffset, labels);
    }
}

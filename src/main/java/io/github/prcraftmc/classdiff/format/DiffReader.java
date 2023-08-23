package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.util.Util;
import io.github.prcraftmc.classdiff.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DiffReader {
    private final PatchReader<String> classPatchReader = new PatchReader<>(reader -> {
        reader.skip(2);
        return readClass(reader.pointer() - 2);
    });
    private final PatchReader<AnnotationNode> annotationPatchReader = new PatchReader<>(reader -> {
        final AnnotationNode result = new AnnotationNode(readUtf8(reader.pointer()));
        reader.pointer(readElementValues(result, reader.pointer() + 2, true));
        return result;
    });
    private final PatchReader<TypeAnnotationNode> typeAnnotationPatchReader = new PatchReader<>(reader -> {
        final Context context = this.context.get();
        reader.pointer(readTypeAnnotationTarget(reader.pointer(), context));
        final TypeAnnotationNode result = new TypeAnnotationNode(
            context.currentTypeAnnotationTarget,
            context.currentTypeAnnotationTargetPath,
            readUtf8(reader.pointer())
        );
        reader.pointer(readElementValues(result, reader.pointer() + 2, true));
        return result;
    });
    private final PatchReader<MemberName> memberNamePatchReader = new PatchReader<>(reader -> {
        reader.skip(4);
        return new MemberName(readUtf8(reader.pointer() - 4), readUtf8(reader.pointer() - 2));
    });
    private final PatchReader<String> packagePatchReader = new PatchReader<>(reader -> {
        reader.skip(2);
        return readPackage(reader.pointer() - 2);
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

        final ByteReader reader = new ByteReader(contents, startPos + 14);

        final Patch<String> interfacePatch;
        {
            if (reader.readShort() == 0) {
                interfacePatch = null;
            } else {
                reader.skip(-2);
                interfacePatch = classPatchReader.readPatch(
                    reader, node.interfaces != null ? node.interfaces : Collections.emptyList()
                );
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

        final int attributeCount = reader.readShort();
        for (int i = 0; i < attributeCount; i++) {
            final String attributeName = readUtf8(reader.pointer());
            if (attributeName == null) {
                throw new IllegalArgumentException("null attribute name at address " + Integer.toHexString(reader.pointer()));
            }
            reader.skip(2);
            final int attributeLength = reader.readInt();
            final int endPos = reader.pointer() + attributeLength;
            switch (attributeName) {
                case "Source":
                    visitor.visitSource(readUtf8(reader.pointer()), readUtf8(reader.pointer() + 2));
                    break;
                case "InnerClasses":
                    visitor.visitInnerClasses(new PatchReader<>(reader1 -> {
                        reader1.skip(6);
                        return new InnerClassNode(
                            readClass(reader1.pointer() - 6),
                            readClass(reader1.pointer() - 4),
                            readUtf8(reader1.pointer() - 2),
                            reader1.readShort()
                        );
                    }).readPatch(
                        reader,
                        node.innerClasses != null ? node.innerClasses : Collections.emptyList()
                    ));
                    break;
                case "OuterClasses":
                    visitor.visitOuterClass(
                        readClass(reader.pointer()),
                        readClass(reader.pointer() + 2),
                        readClass(reader.pointer() + 4)
                    );
                    break;
                case "NestHost":
                    visitor.visitNestHost(readClass(reader.pointer()));
                    break;
                case "NestMembers":
                    visitor.visitNestMembers(classPatchReader.readPatch(
                        reader,
                        node.nestMembers != null ? node.nestMembers : Collections.emptyList()
                    ));
                    break;
                case "PermittedSubclasses":
                    visitor.visitPermittedSubclasses(classPatchReader.readPatch(
                        reader,
                        node.permittedSubclasses != null ? node.permittedSubclasses : Collections.emptyList()
                    ));
                    break;
                case "VisibleAnnotations":
                    visitor.visitAnnotations(annotationPatchReader.readPatch(
                        reader,
                        node.visibleAnnotations != null ? node.visibleAnnotations : Collections.emptyList()
                    ), true);
                    break;
                case "InvisibleAnnotations":
                    visitor.visitAnnotations(annotationPatchReader.readPatch(
                        reader,
                        node.invisibleAnnotations != null ? node.invisibleAnnotations : Collections.emptyList()
                    ), false);
                    break;
                case "VisibleTypeAnnotations":
                    visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                        reader,
                        node.visibleTypeAnnotations != null ? node.visibleTypeAnnotations : Collections.emptyList()
                    ), true);
                    break;
                case "InvisibleTypeAnnotations":
                    visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                        reader,
                        node.invisibleTypeAnnotations != null ? node.invisibleTypeAnnotations : Collections.emptyList()
                    ), false);
                    break;
                case "RecordComponents":
                    visitor.visitRecordComponents(memberNamePatchReader.readPatch(
                        reader, node.recordComponents != null
                            ? MemberName.fromRecordComponents(node.recordComponents) : Collections.emptyList()
                    ));
                    for (int j = 0, l = reader.readShort(); j < l; j++) {
                        readRecordComponent(reader, visitor, node);
                    }
                    break;
                case "Module": {
                    final String name = readModule(reader.pointer());
                    final int access = readShort(reader.pointer() + 2);
                    final String version = readUtf8(reader.pointer() + 4);
                    reader.skip(6);
                    if (name != null) {
                        ModuleNode moduleNode = node.module;
                        if (moduleNode == null) {
                            moduleNode = new ModuleNode(name, access, version); // Temporary
                        }
                        readModule(reader, visitor.visitModule(name, access, version), moduleNode);
                    }
                    break;
                }
                default:
                    if (attributeName.startsWith("Custom")) {
                        if (reader.readByte() != 0) {
                            visitor.visitCustomAttribute(
                                attributeName.substring(6),
                                Arrays.copyOfRange(contents, reader.pointer(), reader.pointer() + attributeLength - 1)
                            );
                        } else {
                            visitor.visitCustomAttribute(attributeName.substring(6), null);
                        }
                    }
                    break;
            }
            reader.pointer(endPos);
        }

        visitor.visitFields(memberNamePatchReader.readPatch(
            reader,
            node.fields != null ? MemberName.fromFields(node.fields) : Collections.emptyList()
        ));
        for (int i = 0, l = reader.readShort(); i < l; i++) {
            readField(reader, visitor, node);
        }

        visitor.visitMethods(memberNamePatchReader.readPatch(
            reader,
            node.methods != null ? MemberName.fromMethods(node.methods) : Collections.emptyList()
        ));
        for (int i = 0, l = reader.readShort(); i < l; i++) {
            readMethod(reader, visitor, node);
        }

        visitor.visitEnd();
        context.remove();
    }

    private void readMethod(ByteReader reader, DiffVisitor diffVisitor, ClassNode classNode) {
        final int access = reader.readInt();
        final String name = readUtf8(reader.pointer());
        final String descriptor = readUtf8(reader.pointer() + 2);
        final String signature = readUtf8(reader.pointer() + 4);
        reader.skip(6);

        MethodNode node = null;
        if (classNode.methods != null) {
            for (final MethodNode test : classNode.methods) {
                if (test.name.equals(name) && test.desc.equals(descriptor)) {
                    node = test;
                    break;
                }
            }
        }
        if (node == null) {
            node = new MethodNode(access, name, descriptor, signature, null);
        }

        final Patch<String> exceptions = classPatchReader.readPatch(
            reader, node.exceptions != null ? node.exceptions : Collections.emptyList()
        );
        final MethodDiffVisitor visitor = diffVisitor.visitMethod(access, name, descriptor, signature, exceptions);

        final int attrCount = reader.readShort();
        for (int i = 0; i < attrCount; i++) {
            reader.skip(2);
            final int attrLength = reader.readInt();
            final int endPos = reader.pointer() + attrLength;
            if (visitor != null) {
                final String attrName = readUtf8(reader.pointer() - 6);
                if (attrName == null) {
                    throw new IllegalArgumentException("null attribute name at address " + Integer.toHexString(reader.pointer() - 6));
                }
                switch (attrName) {
                    case "VisibleAnnotations":
                        visitor.visitAnnotations(annotationPatchReader.readPatch(
                            reader,
                            node.visibleAnnotations != null ? node.visibleAnnotations : Collections.emptyList()
                        ), true);
                        break;
                    case "InvisibleAnnotations":
                        visitor.visitAnnotations(annotationPatchReader.readPatch(
                            reader,
                            node.invisibleAnnotations != null ? node.invisibleAnnotations : Collections.emptyList()
                        ), false);
                        break;
                    case "VisibleTypeAnnotations":
                        visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                            reader,
                            node.visibleTypeAnnotations != null ? node.visibleTypeAnnotations : Collections.emptyList()
                        ), true);
                        break;
                    case "InvisibleTypeAnnotations":
                        visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                            reader,
                            node.invisibleTypeAnnotations != null ? node.invisibleTypeAnnotations : Collections.emptyList()
                        ), false);
                        break;
                    case "AnnotationDefault":
                        if (attrLength > 0) {
                            final AnnotationNode annotationNode = new AnnotationNode("");
                            readElementValue(annotationNode, reader.pointer(), null);
                            visitor.visitAnnotationDefault(annotationNode.values.get(1));
                        } else {
                            visitor.visitAnnotationDefault(null);
                        }
                        break;
                    case "VisibleParameterAnnotations": {
                        final int annotableCount = reader.readByte();
                        final int paramCount = Type.getArgumentTypes(descriptor).length;
                        final List<Patch<AnnotationNode>> patches = new ArrayList<>(paramCount);
                        for (int j = 0; j < paramCount; j++) {
                            patches.add(annotationPatchReader.readPatch(
                                reader, Util.getListFromArray(node.visibleParameterAnnotations, j)
                            ));
                        }
                        visitor.visitParameterAnnotations(annotableCount, patches, true);
                        break;
                    }
                    case "InvisibleParameterAnnotations": {
                        final int annotableCount = reader.readByte();
                        final int paramCount = Type.getArgumentTypes(descriptor).length;
                        final List<Patch<AnnotationNode>> patches = new ArrayList<>(paramCount);
                        for (int j = 0; j < paramCount; j++) {
                            patches.add(annotationPatchReader.readPatch(
                                reader, Util.getListFromArray(node.invisibleParameterAnnotations, j)
                            ));
                        }
                        visitor.visitParameterAnnotations(annotableCount, patches, false);
                        break;
                    }
                    case "MethodParameters":
                        visitor.visitParameters(new PatchReader<>(reader1 -> {
                            reader1.skip(2);
                            return new ParameterNode(readUtf8(reader1.pointer() - 2), reader1.readInt());
                        }).readPatch(reader, node.parameters != null ? node.parameters : Collections.emptyList()));
                        break;
                    case "Maxs":
                        visitor.visitMaxs(reader.readShort(), reader.readShort());
                        break;
                    case "Insns":
                        visitor.visitInsns(
                            new PatchReader<>(this::readInsn).readPatch(reader, new InsnListAdapter(node.instructions)),
                            new LabelMap(node.instructions)
                        );
                        break;
                    default:
                        if (attrName.startsWith("Custom")) {
                            if (reader.readByte() != 0) {
                                visitor.visitCustomAttribute(
                                    attrName.substring(6),
                                    Arrays.copyOfRange(contents, reader.pointer(), reader.pointer() + attrLength - 1)
                                );
                            } else {
                                visitor.visitCustomAttribute(attrName.substring(6), null);
                            }
                        }
                        break;
                }
            }
            reader.pointer(endPos);
        }
    }

    private void readField(ByteReader reader, DiffVisitor diffVisitor, ClassNode classNode) {
        final int access = reader.readInt();
        final String name = readUtf8(reader.pointer());
        final String descriptor = readUtf8(reader.pointer() + 2);
        final String signature = readUtf8(reader.pointer() + 4);
        reader.skip(6);

        final int constantValueIndex = reader.readShort();
        final Object constantValue = constantValueIndex != 0 ? readConst(constantValueIndex) : null;

        final FieldDiffVisitor visitor = diffVisitor.visitField(access, name, descriptor, signature, constantValue);

        FieldNode node = null;
        if (visitor != null) {
            if (classNode.fields != null) {
                for (final FieldNode test : classNode.fields) {
                    if (test.name.equals(name) && test.desc.equals(descriptor)) {
                        node = test;
                        break;
                    }
                }
            }
            if (node == null) {
                node = new FieldNode(access, name, descriptor, signature, constantValue);
            }
        }

        final int attrCount = reader.readShort();
        for (int i = 0; i < attrCount; i++) {
            reader.skip(2);
            final int attrLength = reader.readInt();
            final int endPos = reader.pointer() + attrLength;
            if (visitor != null) {
                final String attrName = readUtf8(reader.pointer() - 6);
                if (attrName == null) {
                    throw new IllegalArgumentException("null attribute name at address " + Integer.toHexString(reader.pointer() - 6));
                }
                switch (attrName) {
                    case "VisibleAnnotations":
                        visitor.visitAnnotations(annotationPatchReader.readPatch(
                            reader,
                            node.visibleAnnotations != null ? node.visibleAnnotations : Collections.emptyList()
                        ), true);
                        break;
                    case "InvisibleAnnotations":
                        visitor.visitAnnotations(annotationPatchReader.readPatch(
                            reader,
                            node.invisibleAnnotations != null ? node.invisibleAnnotations : Collections.emptyList()
                        ), false);
                        break;
                    case "VisibleTypeAnnotations":
                        visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                            reader,
                            node.visibleTypeAnnotations != null ? node.visibleTypeAnnotations : Collections.emptyList()
                        ), true);
                        break;
                    case "InvisibleTypeAnnotations":
                        visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                            reader,
                            node.invisibleTypeAnnotations != null ? node.invisibleTypeAnnotations : Collections.emptyList()
                        ), false);
                        break;
                    default:
                        if (attrName.startsWith("Custom")) {
                            if (reader.readByte() != 0) {
                                visitor.visitCustomAttribute(
                                    attrName.substring(6),
                                    Arrays.copyOfRange(contents, reader.pointer(), reader.pointer() + attrLength - 1)
                                );
                            } else {
                                visitor.visitCustomAttribute(attrName.substring(6), null);
                            }
                        }
                        break;
                }
            }
            reader.pointer(endPos);
        }
    }

    private void readModule(ByteReader reader, ModuleDiffVisitor visitor, ModuleNode node) {
        if (visitor == null) return;

        final int attrCount = reader.readShort();
        for (int i = 0; i < attrCount; i++) {
            final String attrName = readUtf8(reader.pointer());
            if (attrName == null) {
                throw new IllegalArgumentException("null attribute name at address " + Integer.toHexString(reader.pointer()));
            }
            reader.skip(2);
            final int attrLen = reader.readInt();
            final int endPos = reader.pointer() + attrLen;
            switch (attrName) {
                case "MainClass":
                    visitor.visitMainClass(readClass(reader.pointer()));
                    break;
                case "Packages":
                    visitor.visitPackages(packagePatchReader.readPatch(
                        reader, node.packages != null ? node.packages : Collections.emptyList()
                    ));
                    break;
                case "Requires":
                    visitor.visitRequires(new PatchReader<>(reader1 -> {
                        reader1.skip(6);
                        return new ModuleRequireNode(
                            readModule(reader1.pointer() - 6),
                            readShort(reader1.pointer() - 4),
                            readUtf8(reader1.pointer() - 2)
                        );
                    }).readPatch(reader, node.requires != null ? node.requires : Collections.emptyList()));
                    break;
                case "Exports":
                    visitor.visitExports(new PatchReader<>(reader1 -> {
                        final String exports = readPackage(reader1.pointer());
                        reader1.skip(2);
                        final int exportsFlags = reader1.readShort();
                        final List<String> exportsTo = new ArrayList<>();
                        for (int j = 0, l = reader1.readShort(); j < l; j++) {
                            exportsTo.add(readModule(reader1.pointer()));
                            reader1.skip(2);
                        }
                        return new ModuleExportNode(exports, exportsFlags, exportsTo);
                    }).readPatch(reader, node.exports != null ? node.exports : Collections.emptyList()));
                    break;
                case "Opens":
                    visitor.visitOpens(new PatchReader<>(reader1 -> {
                        final String opens = readPackage(reader1.pointer());
                        reader1.skip(2);
                        final int opensFlags = reader1.readShort();
                        final List<String> opensTo = new ArrayList<>();
                        for (int j = 0, l = reader1.readShort(); j < l; j++) {
                            opensTo.add(readModule(reader1.pointer()));
                            reader1.skip(2);
                        }
                        return new ModuleOpenNode(opens, opensFlags, opensTo);
                    }).readPatch(reader, node.opens != null ? node.opens : Collections.emptyList()));
                    break;
                case "Uses":
                    visitor.visitUses(classPatchReader.readPatch(reader, node.uses != null ? node.uses : Collections.emptyList()));
                    break;
                case "Provides":
                    visitor.visitProvides(new PatchReader<>(reader1 -> {
                        final String provides = readClass(reader1.pointer());
                        reader1.skip(2);
                        final List<String> providesWith = new ArrayList<>();
                        for (int j = 0, l = reader1.readShort(); j < l; j++) {
                            providesWith.add(readClass(reader1.pointer()));
                            reader1.skip(2);
                        }
                        return new ModuleProvideNode(provides, providesWith);
                    }).readPatch(reader, node.provides != null ? node.provides : Collections.emptyList()));
                    break;
            }
            reader.pointer(endPos);
        }

        visitor.visitEnd();
    }

    private void readRecordComponent(ByteReader reader, DiffVisitor diffVisitor, ClassNode classNode) {
        final String name = readUtf8(reader.pointer());
        final String descriptor = readUtf8(reader.pointer() + 2);
        final String signature = readUtf8(reader.pointer() + 4);
        reader.skip(6);

        final RecordComponentDiffVisitor visitor = diffVisitor.visitRecordComponent(name, descriptor, signature);

        RecordComponentNode node = null;
        if (visitor != null) {
            if (classNode.recordComponents != null) {
                for (final RecordComponentNode test : classNode.recordComponents) {
                    if (test.name.equals(name) && test.descriptor.equals(descriptor)) {
                        node = test;
                        break;
                    }
                }
            }
            if (node == null) {
                node = new RecordComponentNode(name, descriptor, signature);
            }
        }

        final int attributeCount = reader.readShort();
        for (int i = 0; i < attributeCount; i++) {
            reader.skip(2);
            final int attrLength = reader.readInt();
            final int endPos = reader.pointer() + attrLength;
            if (visitor != null) {
                final String attrName = readUtf8(reader.pointer() - 6);
                if (attrName == null) {
                    throw new IllegalArgumentException("null attribute name at address " + Integer.toHexString(reader.pointer() - 6));
                }
                switch (attrName) {
                    case "VisibleAnnotations":
                        visitor.visitAnnotations(annotationPatchReader.readPatch(
                            reader,
                            node.visibleAnnotations != null ? node.visibleAnnotations : Collections.emptyList()
                        ), true);
                        break;
                    case "InvisibleAnnotations":
                        visitor.visitAnnotations(annotationPatchReader.readPatch(
                            reader,
                            node.invisibleAnnotations != null ? node.invisibleAnnotations : Collections.emptyList()
                        ), false);
                        break;
                    case "VisibleTypeAnnotations":
                        visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                            reader,
                            node.visibleTypeAnnotations != null ? node.visibleTypeAnnotations : Collections.emptyList()
                        ), true);
                        break;
                    case "InvisibleTypeAnnotations":
                        visitor.visitTypeAnnotations(typeAnnotationPatchReader.readPatch(
                            reader,
                            node.invisibleTypeAnnotations != null ? node.invisibleTypeAnnotations : Collections.emptyList()
                        ), false);
                        break;
                    default:
                        if (attrName.startsWith("Custom")) {
                            if (reader.readByte() != 0) {
                                visitor.visitCustomAttribute(
                                    attrName.substring(6),
                                    Arrays.copyOfRange(contents, reader.pointer(), reader.pointer() + attrLength - 1)
                                );
                            } else {
                                visitor.visitCustomAttribute(attrName.substring(6), null);
                            }
                        }
                        break;
                }
            }
            reader.pointer(endPos);
        }

        if (visitor != null) {
            visitor.visitEnd();
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

    private String readModule(int offset) {
        return readStringish(offset);
    }

    private String readPackage(int offset) {
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

    private AbstractInsnNode readInsn(ByteReader reader) {
        final int opcode = reader.readByte();
        switch (opcode) {
            case Opcodes.NOP:
            case Opcodes.ACONST_NULL:
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
            case Opcodes.IALOAD:
            case Opcodes.LALOAD:
            case Opcodes.FALOAD:
            case Opcodes.DALOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.SALOAD:
            case Opcodes.IASTORE:
            case Opcodes.LASTORE:
            case Opcodes.FASTORE:
            case Opcodes.DASTORE:
            case Opcodes.AASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.SASTORE:
            case Opcodes.POP:
            case Opcodes.POP2:
            case Opcodes.DUP:
            case Opcodes.DUP_X1:
            case Opcodes.DUP_X2:
            case Opcodes.DUP2:
            case Opcodes.DUP2_X1:
            case Opcodes.DUP2_X2:
            case Opcodes.SWAP:
            case Opcodes.IADD:
            case Opcodes.LADD:
            case Opcodes.FADD:
            case Opcodes.DADD:
            case Opcodes.ISUB:
            case Opcodes.LSUB:
            case Opcodes.FSUB:
            case Opcodes.DSUB:
            case Opcodes.IMUL:
            case Opcodes.LMUL:
            case Opcodes.FMUL:
            case Opcodes.DMUL:
            case Opcodes.IDIV:
            case Opcodes.LDIV:
            case Opcodes.FDIV:
            case Opcodes.DDIV:
            case Opcodes.IREM:
            case Opcodes.LREM:
            case Opcodes.FREM:
            case Opcodes.DREM:
            case Opcodes.INEG:
            case Opcodes.LNEG:
            case Opcodes.FNEG:
            case Opcodes.DNEG:
            case Opcodes.ISHL:
            case Opcodes.LSHL:
            case Opcodes.ISHR:
            case Opcodes.LSHR:
            case Opcodes.IUSHR:
            case Opcodes.LUSHR:
            case Opcodes.IAND:
            case Opcodes.LAND:
            case Opcodes.IOR:
            case Opcodes.LOR:
            case Opcodes.IXOR:
            case Opcodes.LXOR:
            case Opcodes.I2L:
            case Opcodes.I2F:
            case Opcodes.I2D:
            case Opcodes.L2I:
            case Opcodes.L2F:
            case Opcodes.L2D:
            case Opcodes.F2I:
            case Opcodes.F2L:
            case Opcodes.F2D:
            case Opcodes.D2I:
            case Opcodes.D2L:
            case Opcodes.D2F:
            case Opcodes.I2B:
            case Opcodes.I2C:
            case Opcodes.I2S:
            case Opcodes.LCMP:
            case Opcodes.FCMPL:
            case Opcodes.FCMPG:
            case Opcodes.DCMPL:
            case Opcodes.DCMPG:
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.ARRAYLENGTH:
            case Opcodes.ATHROW:
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
                return new InsnNode(opcode);
            case DiffConstants.ILOAD_0:
            case DiffConstants.ILOAD_1:
            case DiffConstants.ILOAD_2:
            case DiffConstants.ILOAD_3:
            case DiffConstants.LLOAD_0:
            case DiffConstants.LLOAD_1:
            case DiffConstants.LLOAD_2:
            case DiffConstants.LLOAD_3:
            case DiffConstants.FLOAD_0:
            case DiffConstants.FLOAD_1:
            case DiffConstants.FLOAD_2:
            case DiffConstants.FLOAD_3:
            case DiffConstants.DLOAD_0:
            case DiffConstants.DLOAD_1:
            case DiffConstants.DLOAD_2:
            case DiffConstants.DLOAD_3:
            case DiffConstants.ALOAD_0:
            case DiffConstants.ALOAD_1:
            case DiffConstants.ALOAD_2:
            case DiffConstants.ALOAD_3: {
                final int id = opcode - DiffConstants.ILOAD_0;
                return new VarInsnNode(Opcodes.ILOAD + (id >> 2), id & 0x3);
            }
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
            case Opcodes.GOTO:
            case Opcodes.JSR:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                return new JumpInsnNode(opcode, new SyntheticLabelNode(reader.readShort()));
            case DiffConstants.GOTO_W:
            case DiffConstants.JSR_W:
                return new JumpInsnNode(
                    opcode - DiffConstants.GOTO_W + Opcodes.GOTO,
                    new SyntheticLabelNode(reader.readInt())
                );
            case DiffConstants.WIDE: {
                final int secondary = reader.readInt();
                if (secondary == Opcodes.IINC) {
                    return new IincInsnNode(reader.readShort(), (short)reader.readShort());
                }
                return new VarInsnNode(opcode, reader.readShort());
            }
            case Opcodes.TABLESWITCH: {
                final LabelNode defaultLabel = new SyntheticLabelNode(reader.readInt());
                final int low = reader.readInt();
                final int high = reader.readInt();
                final LabelNode[] table = new LabelNode[high - low + 1];
                for (int i = 0; i < table.length; i++) {
                    table[i] = new SyntheticLabelNode(reader.readInt());
                }
                return new TableSwitchInsnNode(low, high, defaultLabel, table);
            }
            case Opcodes.LOOKUPSWITCH: {
                final LabelNode defaultLabel = new SyntheticLabelNode(reader.readInt());
                final int numPairs = reader.readInt();
                final int[] keys = new int[numPairs];
                final LabelNode[] values = new LabelNode[numPairs];
                for (int i = 0; i < numPairs; i++) {
                    keys[i] = reader.readInt();
                    values[i] = new SyntheticLabelNode(reader.readInt());
                }
                return new LookupSwitchInsnNode(defaultLabel, keys, values);
            }
            case Opcodes.ILOAD:
            case Opcodes.LLOAD:
            case Opcodes.FLOAD:
            case Opcodes.DLOAD:
            case Opcodes.ALOAD:
            case Opcodes.ISTORE:
            case Opcodes.LSTORE:
            case Opcodes.FSTORE:
            case Opcodes.DSTORE:
            case Opcodes.ASTORE:
            case Opcodes.RET:
                return new VarInsnNode(opcode, reader.readByte());
            case Opcodes.BIPUSH:
            case Opcodes.NEWARRAY:
                return new IntInsnNode(opcode, (byte)reader.readByte());
            case Opcodes.SIPUSH:
                return new IntInsnNode(opcode, (short)reader.readShort());
            case Opcodes.LDC:
                return new LdcInsnNode(readConst(reader.readByte()));
            case DiffConstants.LDC_W:
            case DiffConstants.LDC2_W:
                if (opcode == DiffConstants.LDC2_W) {
                    throw new IllegalArgumentException("ldc2_w instruction is unsupported. ClassDiff uses plain ldc or ldc_w here.");
                }
                return new LdcInsnNode(readConst(reader.readShort()));
            case Opcodes.GETSTATIC:
            case Opcodes.PUTSTATIC:
            case Opcodes.GETFIELD:
            case Opcodes.PUTFIELD:
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEINTERFACE: {
                final int cpInfoOffset = constantOffsets[reader.readShort()];
                final int nameAndTypeOffset = constantOffsets[readShort(cpInfoOffset + 2)];
                final String owner = readClass(cpInfoOffset);
                final String name = readUtf8(nameAndTypeOffset);
                final String descriptor = readUtf8(nameAndTypeOffset + 2);
                if (opcode < Opcodes.INVOKEVIRTUAL) {
                    return new FieldInsnNode(opcode, owner, name, descriptor);
                }
                return new MethodInsnNode(
                    opcode, owner, name, descriptor,
                    contents[cpInfoOffset - 1] == Symbol.CONSTANT_INTERFACE_METHODREF_TAG
                );
            }
            case Opcodes.INVOKEDYNAMIC: {
                final int cpInfoOffset = constantOffsets[reader.readShort()];
                final int nameAndTypeOffset = constantOffsets[readShort(cpInfoOffset + 2)];
                final String name = readUtf8(nameAndTypeOffset);
                final String descriptor = readUtf8(nameAndTypeOffset + 2);
                int bsmOffset = bsmOffsets[readShort(cpInfoOffset)];
                final Handle handle = (Handle)readConst(readShort(bsmOffset));
                final Object[] bsmArgs = new Object[readShort(bsmOffset + 2)];
                bsmOffset += 4;
                for (int i = 0; i < bsmArgs.length; i++) {
                    bsmArgs[i] = readConst(readShort(bsmOffset));
                    bsmOffset += 2;
                }
                return new InvokeDynamicInsnNode(name, descriptor, handle, bsmArgs);
            }
            case Opcodes.NEW:
            case Opcodes.ANEWARRAY:
            case Opcodes.CHECKCAST:
            case Opcodes.INSTANCEOF:
                reader.skip(2);
                return new TypeInsnNode(opcode, readClass(reader.pointer() - 2));
            case Opcodes.IINC:
                return new IincInsnNode(reader.readByte(), (byte)reader.readByte());
            case Opcodes.MULTIANEWARRAY:
                reader.skip(2);
                return new MultiANewArrayInsnNode(readClass(reader.pointer() - 2), reader.readByte());
            case 255: { // Special insn
                final int specialType = reader.readByte();
                switch (specialType) {
                    case AbstractInsnNode.LABEL:
                        return new LabelNode();
                    case AbstractInsnNode.FRAME: {
                        final int frameType = reader.readByte();
                        final int numLocal, numStack;
                        switch (frameType) {
                            case (byte)Opcodes.F_NEW:
                            case Opcodes.F_FULL:
                                numLocal = reader.readShort();
                                numStack = reader.readShort();
                                return new FrameNode(
                                    frameType,
                                    numLocal, readFrameObjects(numLocal, reader),
                                    numStack, readFrameObjects(numStack, reader)
                                );
                            case Opcodes.F_APPEND:
                                numLocal = reader.readShort();
                                return new FrameNode(
                                    Opcodes.F_APPEND,
                                    numLocal, readFrameObjects(numLocal, reader),
                                    0, null
                                );
                            case Opcodes.F_CHOP:
                                numLocal = reader.readShort();
                                return new FrameNode(
                                    Opcodes.F_CHOP,
                                    numLocal, null,
                                    0, null
                                );
                            case Opcodes.F_SAME:
                                return new FrameNode(
                                    Opcodes.F_SAME,
                                    0, null,
                                    0, null
                                );
                            case Opcodes.F_SAME1:
                                return new FrameNode(
                                    Opcodes.F_SAME1,
                                    0, null,
                                    0, readFrameObjects(1, reader)
                                );
                            default:
                                throw new IllegalArgumentException("Unknown frame type " + frameType);
                        }
                    }
                    case AbstractInsnNode.LINE:
                        return new LineNumberNode(reader.readShort(), new SyntheticLabelNode(reader.readShort()));
                    default:
                        throw new IllegalArgumentException("Unknown special insn type " + specialType);
                }
            }
            default:
                throw new IllegalArgumentException("Unknown opcode 0x" + Integer.toHexString(opcode));
        }
    }

    private Object[] readFrameObjects(int count, ByteReader reader) {
        final Object[] result = new Object[count];
        for (int i = 0; i < count; i++) {
            result[i] = readFrameObject(reader);
        }
        return result;
    }

    private Object readFrameObject(ByteReader reader) {
        final int tag = reader.readByte();
        switch (tag) {
            case Frame.ITEM_TOP:
            case Frame.ITEM_INTEGER:
            case Frame.ITEM_FLOAT:
            case Frame.ITEM_DOUBLE:
            case Frame.ITEM_LONG:
            case Frame.ITEM_NULL:
            case Frame.ITEM_UNINITIALIZED_THIS:
                return tag;
            case Frame.ITEM_OBJECT:
                reader.skip(2);
                return readClass(reader.pointer() - 2);
            case Frame.ITEM_UNINITIALIZED:
                return new SyntheticLabelNode(reader.readShort());
            default:
                throw new IllegalArgumentException("Unknown frame object type tag " + tag);
        }
    }
}

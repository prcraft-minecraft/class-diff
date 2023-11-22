package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.util.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.function.Supplier;

public class DiffWriter extends DiffVisitor {
    private final SymbolTable symbolTable = new SymbolTable();
    private final PatchWriter<String> classPatchWriter = new PatchWriter<>((vec, value) ->
        vec.putShort(symbolTable.addConstantClass(value).index)
    );
    private final PatchWriter<AnnotationNode> annotationPatchWriter = new PatchWriter<>((vec, value) -> {
        vec.putShort(symbolTable.addConstantUtf8(value.desc)).putShort(0);
        value.accept(new AnnotationWriter(symbolTable, true, vec));
    });
    private final PatchWriter<TypeAnnotationNode> typeAnnotationPatchWriter = new PatchWriter<>((vector, annotation) ->
        writeTypeAnnotation(vector, annotation, false)
    );
    private final PatchWriter<MemberName> memberNamePatchWriter = new PatchWriter<>((vec, value) ->
        vec.putShort(symbolTable.addConstantNameAndType(value.name, value.descriptor))
    );
    private final PatchWriter<String> packagePatchWriter = new PatchWriter<>((vec, value) ->
        vec.putShort(symbolTable.addConstantPackage(value).index)
    );

    private int diffVersion;
    private int classVersion;
    private int access;
    private int name;
    private int signature;
    private int superName;
    private ByteVector interfaces;

    private int source;
    private int debug;

    private ByteVector innerClasses;

    private int outerClass;
    private int outerMethod;
    private int outerMethodDesc;

    private int nestHost;

    private ByteVector nestMembers;

    private ByteVector permittedSubclasses;

    private ByteVector visibleAnnotations;
    private ByteVector invisibleAnnotations;

    private ByteVector visibleTypeAnnotations;
    private ByteVector invisibleTypeAnnotations;

    private ByteVector recordComponentsPatch;
    private final List<ByteVector> recordComponents = new ArrayList<>();

    private ByteVector module;

    private ByteVector fieldsPatch;
    private final List<ByteVector> fields = new ArrayList<>();

    private ByteVector methodsPatch;
    private final List<ByteVector> methods = new ArrayList<>();

    private final Map<Integer, byte @Nullable []> customAttributes = new LinkedHashMap<>();

    public DiffWriter() {
    }

    public DiffWriter(@Nullable DiffVisitor delegate) {
        super(delegate);
    }

    @Override
    public void visit(
        int diffVersion,
        int classVersion,
        int access,
        @Nullable String name,
        @Nullable String signature,
        @Nullable String superName,
        @Nullable Patch<String> interfaces
    ) {
        super.visit(diffVersion, classVersion, access, name, signature, superName, interfaces);

        this.diffVersion = diffVersion;
        this.classVersion = classVersion;
        this.access = access;
        this.name = name != null ? symbolTable.addConstantClass(name).index : 0;
        this.signature = signature != null ? symbolTable.addConstantUtf8(signature) : 0;
        this.superName = superName != null ? symbolTable.addConstantClass(superName).index : 0;

        if (interfaces != null) {
            classPatchWriter.write(this.interfaces = new ByteVector(), interfaces);
        } else {
            this.interfaces = null;
        }
    }

    @Override
    public void visitSource(@Nullable String source, @Nullable String debug) {
        super.visitSource(source, debug);

        this.source = source != null ? symbolTable.addConstantUtf8(source) : 0;
        this.debug = debug != null ? symbolTable.addConstantUtf8(debug) : 0;
    }

    @Override
    public void visitInnerClasses(Patch<InnerClassNode> patch) {
        super.visitInnerClasses(patch);

        new PatchWriter<InnerClassNode>((vec, value) ->
            vec.putShort(symbolTable.addConstantClass(value.name).index)
                .putShort(value.outerName != null ? symbolTable.addConstantClass(value.outerName).index : 0)
                .putShort(value.innerName != null ? symbolTable.addConstantUtf8(value.innerName) : 0)
                .putShort(value.access)
        ).write(innerClasses = new ByteVector(), patch);
    }

    @Override
    public void visitOuterClass(
        @Nullable String className,
        @Nullable String methodName,
        @Nullable String methodDescriptor
    ) {
        super.visitOuterClass(className, methodName, methodDescriptor);

        outerClass = className != null ? symbolTable.addConstantClass(className).index : 0;
        outerMethod = methodName != null ? symbolTable.addConstantUtf8(methodName) : 0;
        outerMethodDesc = methodDescriptor != null ? symbolTable.addConstantUtf8(methodDescriptor) : 0;
    }

    @Override
    public void visitNestHost(@Nullable String nestHost) {
        super.visitNestHost(nestHost);

        this.nestHost = nestHost != null ? symbolTable.addConstantClass(nestHost).index : 0;
    }

    @Override
    public void visitNestMembers(Patch<String> patch) {
        super.visitNestMembers(patch);

        classPatchWriter.write(nestMembers = new ByteVector(), patch);
    }

    @Override
    public void visitPermittedSubclasses(Patch<String> patch) {
        super.visitPermittedSubclasses(patch);

        classPatchWriter.write(permittedSubclasses = new ByteVector(), patch);
    }

    @Override
    public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
        super.visitAnnotations(patch, visible);

        final ByteVector vector = new ByteVector();
        if (visible) {
            visibleAnnotations = vector;
        } else {
            invisibleAnnotations = vector;
        }
        annotationPatchWriter.write(vector, patch);
    }

    @Override
    public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
        super.visitTypeAnnotations(patch, visible);

        final ByteVector vector = new ByteVector();
        if (visible) {
            visibleTypeAnnotations = vector;
        } else {
            invisibleTypeAnnotations = vector;
        }
        typeAnnotationPatchWriter.write(vector, patch);
    }

    @Override
    public void visitRecordComponents(Patch<MemberName> patch) {
        super.visitRecordComponents(patch);

        memberNamePatchWriter.write(recordComponentsPatch = new ByteVector(), patch);
    }

    @Override
    public RecordComponentDiffVisitor visitRecordComponent(String name, String descriptor, @Nullable String signature) {
        final RecordComponentDiffVisitor delegate = super.visitRecordComponent(name, descriptor, signature);

        final ByteVector vector = new ByteVector();
        recordComponents.add(vector);

        vector.putShort(symbolTable.addConstantUtf8(name));
        vector.putShort(symbolTable.addConstantUtf8(descriptor));
        vector.putShort(signature != null ? symbolTable.addConstantUtf8(signature) : 0);

        vector.putShort(0);
        return new RecordComponentDiffVisitor(delegate) {
            final int countIndex = vector.size() - 2;
            int sizeIndex;
            int attributeCount;

            @Override
            public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
                super.visitAnnotations(patch, visible);

                beginAttr((visible ? "Visible" : "Invisible") + "Annotations");
                annotationPatchWriter.write(vector, patch);
                endAttr();
            }

            @Override
            public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
                super.visitTypeAnnotations(patch, visible);

                beginAttr((visible ? "Visible" : "Invisible") + "TypeAnnotations");
                typeAnnotationPatchWriter.write(vector, patch);
                endAttr();
            }

            @Override
            public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
                super.visitCustomAttribute(name, patchOrContents);

                vector.putShort(symbolTable.addConstantUtf8("Custom" + name));
                if (patchOrContents == null) {
                    vector.putInt(1).putByte(0);
                } else {
                    vector.putInt(patchOrContents.length + 1)
                        .putByte(1)
                        .putByteArray(patchOrContents, 0, patchOrContents.length);
                }
                attributeCount++;
            }

            @Override
            public void visitEnd() {
                super.visitEnd();

                final byte[] data = ReflectUtils.getByteVectorData(vector);
                data[countIndex] = (byte)(attributeCount >> 8);
                data[countIndex + 1] = (byte)attributeCount;
            }

            private void beginAttr(String name) {
                vector.putShort(symbolTable.addConstantUtf8(name));
                sizeIndex = vector.size();
                vector.putInt(0);
            }

            private void endAttr() {
                final byte[] data = ReflectUtils.getByteVectorData(vector);
                final int index = sizeIndex;
                final int size = vector.size() - sizeIndex - 4;
                data[index] = (byte)(size >>> 24);
                data[index + 1] = (byte)(size >> 16);
                data[index + 2] = (byte)(size >> 8);
                data[index + 3] = (byte)size;
                attributeCount++;
            }
        };
    }

    @Override
    public ModuleDiffVisitor visitModule(@Nullable String name, int access, @Nullable String version) {
        final ModuleDiffVisitor delegate = super.visitModule(name, access, version);

        final ByteVector vector = new ByteVector();
        module = vector;

        vector.putShort(name != null ? symbolTable.addConstantModule(name).index : 0);
        vector.putShort(access);
        vector.putShort(version != null ? symbolTable.addConstantUtf8(version) : 0);
        vector.putShort(0);
        return new ModuleDiffVisitor(delegate) {
            final int countIndex = vector.size() - 2;
            int sizeIndex;
            int attributeCount;

            @Override
            public void visitMainClass(@Nullable String mainClass) {
                super.visitMainClass(mainClass);

                beginAttr("MainClass");
                vector.putShort(mainClass != null ? symbolTable.addConstantClass(mainClass).index : 0);
                endAttr();
            }

            @Override
            public void visitPackages(Patch<String> patch) {
                super.visitPackages(patch);

                beginAttr("Packages");
                packagePatchWriter.write(vector, patch);
                endAttr();
            }

            @Override
            public void visitRequires(Patch<ModuleRequireNode> patch) {
                super.visitRequires(patch);

                beginAttr("Requires");
                new PatchWriter<ModuleRequireNode>((vec, value) -> {
                    vec.putShort(symbolTable.addConstantModule(value.module).index);
                    vec.putShort(value.access);
                    vec.putShort(value.version != null ? symbolTable.addConstantUtf8(value.version) : 0);
                }).write(vector, patch);
                endAttr();
            }

            @Override
            public void visitExports(Patch<ModuleExportNode> patch) {
                super.visitExports(patch);

                beginAttr("Exports");
                new PatchWriter<ModuleExportNode>((vec, value) -> {
                    vec.putShort(symbolTable.addConstantPackage(value.packaze).index);
                    vec.putShort(value.access);
                    if (value.modules != null) {
                        vec.putShort(value.modules.size());
                        for (final String module : value.modules) {
                            vec.putShort(symbolTable.addConstantModule(module).index);
                        }
                    } else {
                        vec.putShort(0);
                    }
                }).write(vector, patch);
                endAttr();
            }

            @Override
            public void visitOpens(Patch<ModuleOpenNode> patch) {
                super.visitOpens(patch);

                beginAttr("Opens");
                new PatchWriter<ModuleOpenNode>((vec, value) -> {
                    vec.putShort(symbolTable.addConstantPackage(value.packaze).index);
                    vec.putShort(value.access);
                    if (value.modules != null) {
                        vec.putShort(value.modules.size());
                        for (final String module : value.modules) {
                            vec.putShort(symbolTable.addConstantModule(module).index);
                        }
                    } else {
                        vec.putShort(0);
                    }
                }).write(vector, patch);
                endAttr();
            }

            @Override
            public void visitUses(Patch<String> patch) {
                super.visitUses(patch);

                beginAttr("Uses");
                classPatchWriter.write(vector, patch);
                endAttr();
            }

            @Override
            public void visitProvides(Patch<ModuleProvideNode> patch) {
                super.visitProvides(patch);

                beginAttr("Provides");
                new PatchWriter<ModuleProvideNode>((vec, value) -> {
                    vec.putShort(symbolTable.addConstantClass(value.service).index);
                    if (value.providers != null) {
                        vec.putShort(value.providers.size());
                        for (final String provider : value.providers) {
                            vec.putShort(symbolTable.addConstantClass(provider).index);
                        }
                    } else {
                        vec.putShort(0);
                    }
                }).write(vector, patch);
                endAttr();
            }

            @Override
            public void visitEnd() {
                super.visitEnd();

                final byte[] data = ReflectUtils.getByteVectorData(vector);
                data[countIndex] = (byte)(attributeCount >> 8);
                data[countIndex + 1] = (byte)attributeCount;
            }

            private void beginAttr(String name) {
                vector.putShort(symbolTable.addConstantUtf8(name));
                sizeIndex = vector.size();
                vector.putInt(0);
            }

            private void endAttr() {
                final byte[] data = ReflectUtils.getByteVectorData(vector);
                final int index = sizeIndex;
                final int size = vector.size() - sizeIndex - 4;
                data[index] = (byte)(size >>> 24);
                data[index + 1] = (byte)(size >> 16);
                data[index + 2] = (byte)(size >> 8);
                data[index + 3] = (byte)size;
                attributeCount++;
            }
        };
    }

    @Override
    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        super.visitCustomAttribute(name, patchOrContents);

        customAttributes.put(symbolTable.addConstantUtf8("Custom" + name), patchOrContents);
    }

    @Override
    public void visitFields(Patch<MemberName> patch) {
        super.visitFields(patch);

        memberNamePatchWriter.write(fieldsPatch = new ByteVector(), patch);
    }

    @Override
    public FieldDiffVisitor visitField(
        int access,
        String name,
        String descriptor,
        @Nullable String signature,
        @Nullable Object value
    ) {
        final FieldDiffVisitor delegate = super.visitField(access, name, descriptor, signature, value);

        final ByteVector vector = new ByteVector();
        fields.add(vector);

        vector.putInt(access);
        vector.putShort(symbolTable.addConstantUtf8(name));
        vector.putShort(symbolTable.addConstantUtf8(descriptor));
        vector.putShort(signature != null ? symbolTable.addConstantUtf8(signature) : 0);
        vector.putShort(value != null ? symbolTable.addConstant(value).index : 0);

        vector.putShort(0);
        return new FieldDiffVisitor(delegate) {
            final int countIndex = vector.size() - 2;
            int sizeIndex;
            int attributeCount;

            @Override
            public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
                super.visitAnnotations(patch, visible);

                beginAttr((visible ? "Visible" : "Invisible") + "Annotations");
                annotationPatchWriter.write(vector, patch);
                endAttr();
            }

            @Override
            public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
                super.visitTypeAnnotations(patch, visible);

                beginAttr((visible ? "Visible" : "Invisible") + "TypeAnnotations");
                typeAnnotationPatchWriter.write(vector, patch);
                endAttr();
            }

            @Override
            public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
                super.visitCustomAttribute(name, patchOrContents);

                vector.putShort(symbolTable.addConstantUtf8("Custom" + name));
                if (patchOrContents == null) {
                    vector.putInt(1).putByte(0);
                } else {
                    vector.putInt(patchOrContents.length + 1)
                        .putByte(1)
                        .putByteArray(patchOrContents, 0, patchOrContents.length);
                }
                attributeCount++;
            }

            @Override
            public void visitEnd() {
                super.visitEnd();

                final byte[] data = ReflectUtils.getByteVectorData(vector);
                data[countIndex] = (byte)(attributeCount >> 8);
                data[countIndex + 1] = (byte)attributeCount;
            }

            private void beginAttr(String name) {
                vector.putShort(symbolTable.addConstantUtf8(name));
                sizeIndex = vector.size();
                vector.putInt(0);
            }

            private void endAttr() {
                final byte[] data = ReflectUtils.getByteVectorData(vector);
                final int index = sizeIndex;
                final int size = vector.size() - sizeIndex - 4;
                data[index] = (byte)(size >>> 24);
                data[index + 1] = (byte)(size >> 16);
                data[index + 2] = (byte)(size >> 8);
                data[index + 3] = (byte)size;
                attributeCount++;
            }
        };
    }

    @Override
    public void visitMethods(Patch<MemberName> patch) {
        super.visitMethods(patch);

        memberNamePatchWriter.write(methodsPatch = new ByteVector(), patch);
    }

    @Override
    public MethodDiffVisitor visitMethod(
        int access,
        String name,
        String descriptor,
        @Nullable String signature,
        Patch<String> exceptions
    ) {
        final MethodDiffVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);

        final ByteVector vector = new ByteVector();
        methods.add(vector);

        vector.putInt(access);
        vector.putShort(symbolTable.addConstantUtf8(name));
        vector.putShort(symbolTable.addConstantUtf8(descriptor));
        vector.putShort(signature != null ? symbolTable.addConstantUtf8(signature) : 0);
        classPatchWriter.write(vector, exceptions);

        vector.putShort(0);
        return new MethodDiffVisitor(delegate) {
            final int countIndex = vector.size() - 2;
            int sizeIndex;
            int attributeCount;

            LabelMap labelMap;

            @Override
            public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
                super.visitAnnotations(patch, visible);

                beginAttr((visible ? "Visible" : "Invisible") + "Annotations");
                annotationPatchWriter.write(vector, patch);
                endAttr();
            }

            @Override
            public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
                super.visitTypeAnnotations(patch, visible);

                beginAttr((visible ? "Visible" : "Invisible") + "TypeAnnotations");
                typeAnnotationPatchWriter.write(vector, patch);
                endAttr();
            }

            @Override
            public void visitAnnotationDefault(Object value) {
                super.visitAnnotationDefault(value);

                beginAttr("AnnotationDefault");
                if (value != null) {
                    final AnnotationNode annotationNode = new AnnotationNode("");
                    annotationNode.values = new ArrayList<>();
                    annotationNode.values.add("");
                    annotationNode.values.add(value);
                    annotationNode.accept(new AnnotationWriter(symbolTable, false, vector));
                }
                endAttr();
            }

            @Override
            public void visitParameterAnnotations(int annotableCount, List<Patch<AnnotationNode>> patches, boolean visible) {
                super.visitParameterAnnotations(annotableCount, patches, visible);

                beginAttr((visible ? "Visible" : "Invisible") + "ParameterAnnotations");
                vector.putByte(annotableCount);
                for (final Patch<AnnotationNode> patch : patches) {
                    annotationPatchWriter.write(vector, patch);
                }
                endAttr();
            }

            @Override
            public void visitParameters(Patch<ParameterNode> parameters) {
                super.visitParameters(parameters);

                beginAttr("MethodParameters");
                new PatchWriter<ParameterNode>((vec, value) ->
                    vec.putShort(symbolTable.addConstantUtf8(value.name)).putInt(value.access)
                ).write(vector, parameters);
                endAttr();
            }

            @Override
            public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
                super.visitCustomAttribute(name, patchOrContents);

                vector.putShort(symbolTable.addConstantUtf8("Custom" + name));
                if (patchOrContents == null) {
                    vector.putInt(1).putByte(0);
                } else {
                    vector.putInt(patchOrContents.length + 1)
                        .putByte(1)
                        .putByteArray(patchOrContents, 0, patchOrContents.length);
                }
                attributeCount++;
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                super.visitMaxs(maxStack, maxLocals);

                vector.putShort(symbolTable.addConstantUtf8("Maxs")).putInt(4);
                vector.putShort(maxStack).putShort(maxLocals);
                attributeCount++;
            }

            @Override
            public void visitInsns(int unpatchedInsnCount, Patch<AbstractInsnNode> patch, Supplier<LabelMap> patchedLabelMap) {
                super.visitInsns(unpatchedInsnCount, patch, patchedLabelMap);

                beginAttr("Insns");
                labelMap = patchedLabelMap.get();
                vector.putShort(unpatchedInsnCount);
                new PatchWriter<AbstractInsnNode>(
                    (vec, value) -> writeInsn(vec, value, labelMap)
                ).write(vector, patch);
                endAttr();
            }

            @Override
            public void visitLocalVariables(List<LocalVariableNode> newLocals, @Nullable LabelMap useMap) {
                if (useMap == null && labelMap == null && !newLocals.stream().allMatch(
                    l -> l.start instanceof SyntheticLabelNode && l.end instanceof SyntheticLabelNode
                )) {
                    throw new IllegalStateException(
                        "Cannot call DiffWriter.visitLocalVariables() unless one of the following conditions is met:\n" +
                            "  + useMap is not null\n" +
                            "  + visitInsns has been called first\n" +
                            "  + All labels used in the local variables are synthetic"
                    );
                }

                super.visitLocalVariables(newLocals, useMap);

                if (useMap == null) {
                    useMap = labelMap != null ? labelMap : new LabelMap();
                }

                vector.putShort(symbolTable.addConstantUtf8("LocalVariables")).putInt(2 + 12 * newLocals.size());
                vector.putShort(newLocals.size());
                for (final LocalVariableNode variable : newLocals) {
                    vector.putShort(symbolTable.addConstantUtf8(variable.name));
                    vector.putShort(symbolTable.addConstantUtf8(variable.desc));
                    vector.putShort(variable.signature != null ? symbolTable.addConstantUtf8(variable.signature) : 0);
                    vector.putShort(useMap.getId(variable.start));
                    vector.putShort(useMap.getId(variable.end));
                    vector.putShort(variable.index);
                }
                attributeCount++;
            }

            @Override
            public void visitTryCatchBlocks(List<TryCatchBlockNode> newBlocks, @Nullable LabelMap useMap) {
                if (useMap == null && labelMap == null && !newBlocks.stream().allMatch(
                    l -> l.start instanceof SyntheticLabelNode && l.end instanceof SyntheticLabelNode && l.handler instanceof SyntheticLabelNode
                )) {
                    throw new IllegalStateException(
                        "Cannot call DiffWriter.visitTryCatchBlocks() unless one of the following conditions is met:\n" +
                            "  + useMap is not null\n" +
                            "  + visitInsns has been called first\n" +
                            "  + All labels used in the try-catch blocks are synthetic"
                    );
                }

                super.visitTryCatchBlocks(newBlocks, useMap);

                if (useMap == null) {
                    useMap = labelMap != null ? labelMap : new LabelMap();
                }

                beginAttr("TryCatchBlocks");
                vector.putShort(newBlocks.size());
                for (final TryCatchBlockNode block : newBlocks) {
                    vector.putShort(useMap.getId(block.start));
                    vector.putShort(useMap.getId(block.end));
                    vector.putShort(useMap.getId(block.handler));
                    vector.putShort(block.type != null ? symbolTable.addConstantClass(block.type).index : 0);

                    if (block.invisibleTypeAnnotations != null) {
                        vector.putShort(block.invisibleTypeAnnotations.size());
                        for (final TypeAnnotationNode annotation : block.invisibleTypeAnnotations) {
                            writeTypeAnnotation(vector, annotation, false);
                        }
                    } else {
                        vector.putShort(0);
                    }

                    if (block.visibleTypeAnnotations != null) {
                        vector.putShort(block.visibleTypeAnnotations.size());
                        for (final TypeAnnotationNode annotation : block.visibleTypeAnnotations) {
                            writeTypeAnnotation(vector, annotation, false);
                        }
                    } else {
                        vector.putShort(0);
                    }
                }
                endAttr();
            }

            @Override
            public void visitLocalVariableAnnotations(List<LocalVariableAnnotationNode> annotations, boolean visible, @Nullable LabelMap useMap) {
                if (useMap == null && labelMap == null && !annotations.stream().allMatch(
                    l -> l.start.stream().allMatch(l2 -> l2 instanceof SyntheticLabelNode) &&
                        l.end.stream().allMatch(l2 -> l2 instanceof SyntheticLabelNode)
                )) {
                    throw new IllegalStateException(
                        "Cannot call DiffWriter.visitLocalVariableAnnotations() unless one of the following conditions is met:\n" +
                            "  + useMap is not null\n" +
                            "  + visitInsns has been called first\n" +
                            "  + All labels used in the annotations are synthetic"
                    );
                }

                super.visitLocalVariableAnnotations(annotations, visible, useMap);

                if (useMap == null) {
                    useMap = labelMap != null ? labelMap : new LabelMap();
                }

                beginAttr((visible ? "Visible" : "Invisible") + "LocalVariableAnnotations");
                vector.putShort(annotations.size());
                for (final LocalVariableAnnotationNode annotation : annotations) {
                    writeTypeAnnotation(vector, annotation, true);

                    vector.putShort(annotation.start.size());
                    for (final LabelNode start : annotation.start) {
                        vector.putShort(useMap.getId(start));
                    }

                    vector.putShort(annotation.end.size());
                    for (final LabelNode end : annotation.end) {
                        vector.putShort(useMap.getId(end));
                    }

                    vector.putShort(annotation.index.size());
                    for (final int index : annotation.index) {
                        vector.putShort(index);
                    }
                }
                endAttr();
            }

            @Override
            public void visitInsnAnnotations(int[] indices, List<TypeAnnotationNode> annotations, boolean visible) {
                super.visitInsnAnnotations(indices, annotations, visible);

                beginAttr((visible ? "Visible" : "Invisible") + "InsnAnnotations");
                vector.putShort(indices.length);
                for (int i = 0; i < indices.length; i++) {
                    vector.putShort(indices[i]);
                    writeTypeAnnotation(vector, annotations.get(i), false);
                }
                endAttr();
            }

            @Override
            public void visitEnd() {
                super.visitEnd();

                final byte[] data = ReflectUtils.getByteVectorData(vector);
                data[countIndex] = (byte)(attributeCount >> 8);
                data[countIndex + 1] = (byte)attributeCount;
            }

            private void beginAttr(String name) {
                vector.putShort(symbolTable.addConstantUtf8(name));
                sizeIndex = vector.size();
                vector.putInt(0);
            }

            private void endAttr() {
                final byte[] data = ReflectUtils.getByteVectorData(vector);
                final int index = sizeIndex;
                final int size = vector.size() - sizeIndex - 4;
                data[index] = (byte)(size >>> 24);
                data[index + 1] = (byte)(size >> 16);
                data[index + 2] = (byte)(size >> 8);
                data[index + 3] = (byte)size;
                attributeCount++;
            }
        };
    }

    public byte[] toByteArray() {
        final ByteVector result = new ByteVector();

        result.putInt(DiffConstants.MAGIC);
        result.putShort(diffVersion);

        int attributeCount = customAttributes.size();
        if (symbolTable.computeBootstrapMethodsSize() > 0) {
            attributeCount++;
        }
        if (source != 0 || debug != 0) {
            symbolTable.addConstantUtf8("Source");
            attributeCount++;
        }
        if (innerClasses != null) {
            symbolTable.addConstantUtf8("InnerClasses");
            attributeCount++;
        }
        if (outerClass != 0 || outerMethod != 0 || outerMethodDesc != 0) {
            symbolTable.addConstantUtf8("OuterClass");
            attributeCount++;
        }
        if (nestHost != 0) {
            symbolTable.addConstantUtf8("NestHost");
            attributeCount++;
        }
        if (nestMembers != null) {
            symbolTable.addConstantUtf8("NestMembers");
            attributeCount++;
        }
        if (permittedSubclasses != null) {
            symbolTable.addConstantUtf8("PermittedSubclasses");
            attributeCount++;
        }
        if (visibleAnnotations != null) {
            symbolTable.addConstantUtf8("VisibleAnnotations");
            attributeCount++;
        }
        if (invisibleAnnotations != null) {
            symbolTable.addConstantUtf8("InvisibleAnnotations");
            attributeCount++;
        }
        if (visibleTypeAnnotations != null) {
            symbolTable.addConstantUtf8("VisibleTypeAnnotations");
            attributeCount++;
        }
        if (invisibleTypeAnnotations != null) {
            symbolTable.addConstantUtf8("InvisibleTypeAnnotations");
            attributeCount++;
        }
        if (recordComponentsPatch != null || !recordComponents.isEmpty()) {
            symbolTable.addConstantUtf8("RecordComponents");
            attributeCount++;
        }
        if (module != null) {
            symbolTable.addConstantUtf8("Module");
            attributeCount++;
        }

        symbolTable.putConstantPool(result);

        result.putInt(classVersion);
        result.putInt(access);
        result.putShort(name);
        result.putShort(signature);
        result.putShort(superName);

        if (interfaces == null) {
            result.putShort(0);
        } else {
            result.putByteArray(ReflectUtils.getByteVectorData(interfaces), 0, interfaces.size());
        }

        result.putShort(attributeCount);
        symbolTable.putBootstrapMethods(result);
        if (source != 0 || debug != 0) {
            result.putShort(symbolTable.addConstantUtf8("Source")).putInt(4);
            result.putShort(source).putShort(debug);
        }
        if (innerClasses != null) {
            result.putShort(symbolTable.addConstantUtf8("InnerClasses")).putInt(innerClasses.size());
            result.putByteArray(ReflectUtils.getByteVectorData(innerClasses), 0, innerClasses.size());
        }
        if (outerClass != 0 || outerMethod != 0 || outerMethodDesc != 0) {
            result.putShort(symbolTable.addConstantUtf8("OuterClass")).putInt(6);
            result.putShort(outerClass).putShort(outerMethod).putShort(outerMethodDesc);
        }
        if (nestHost != 0) {
            result.putShort(symbolTable.addConstantUtf8("NestHost")).putInt(2);
            result.putShort(nestHost);
        }
        if (nestMembers != null) {
            result.putShort(symbolTable.addConstantUtf8("NestMembers")).putInt(nestMembers.size());
            result.putByteArray(ReflectUtils.getByteVectorData(nestMembers), 0, nestMembers.size());
        }
        if (permittedSubclasses != null) {
            result.putShort(symbolTable.addConstantUtf8("PermittedSubclasses")).putInt(permittedSubclasses.size());
            result.putByteArray(ReflectUtils.getByteVectorData(permittedSubclasses), 0, permittedSubclasses.size());
        }
        if (visibleAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("VisibleAnnotations")).putInt(visibleAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(visibleAnnotations), 0, visibleAnnotations.size());
        }
        if (invisibleAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("InvisibleAnnotations")).putInt(invisibleAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(invisibleAnnotations), 0, invisibleAnnotations.size());
        }
        if (visibleTypeAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("VisibleTypeAnnotations")).putInt(visibleTypeAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(visibleTypeAnnotations), 0, visibleTypeAnnotations.size());
        }
        if (invisibleTypeAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("InvisibleTypeAnnotations")).putInt(invisibleTypeAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(invisibleTypeAnnotations), 0, invisibleTypeAnnotations.size());
        }
        if (recordComponentsPatch != null || !recordComponents.isEmpty()) {
            int size = 0;
            if (recordComponentsPatch != null) {
                size += recordComponentsPatch.size();
            } else {
                size += 2;
            }
            size += 2;
            for (final ByteVector component : recordComponents) {
                size += component.size();
            }
            result.putShort(symbolTable.addConstantUtf8("RecordComponents")).putInt(size);
            if (recordComponentsPatch != null) {
                result.putByteArray(ReflectUtils.getByteVectorData(recordComponentsPatch), 0, recordComponentsPatch.size());
            } else {
                result.putShort(0);
            }
            result.putShort(recordComponents.size());
            for (final ByteVector component : recordComponents) {
                result.putByteArray(ReflectUtils.getByteVectorData(component), 0, component.size());
            }
        }
        if (module != null) {
            result.putShort(symbolTable.addConstantUtf8("Module")).putInt(module.size());
            result.putByteArray(ReflectUtils.getByteVectorData(module), 0, module.size());
        }
        for (final Map.Entry<Integer, byte @Nullable []> entry : customAttributes.entrySet()) {
            result.putShort(entry.getKey());
            final byte @Nullable [] value = entry.getValue();
            if (value == null) {
                result.putInt(1).putByte(0);
            } else {
                result.putInt(value.length + 1)
                    .putByte(1)
                    .putByteArray(value, 0, value.length);
            }
        }

        if (fieldsPatch != null) {
            result.putByteArray(ReflectUtils.getByteVectorData(fieldsPatch), 0, fieldsPatch.size());
        } else {
            result.putShort(0);
        }
        result.putShort(fields.size());
        for (final ByteVector field : fields) {
            result.putByteArray(ReflectUtils.getByteVectorData(field), 0, field.size());
        }

        if (methodsPatch != null) {
            result.putByteArray(ReflectUtils.getByteVectorData(methodsPatch), 0, methodsPatch.size());
        } else {
            result.putShort(0);
        }
        result.putShort(methods.size());
        for (final ByteVector method : methods) {
            result.putByteArray(ReflectUtils.getByteVectorData(method), 0, method.size());
        }

        return Arrays.copyOf(ReflectUtils.getByteVectorData(result), result.size());
    }

    private void writeInsn(ByteVector vector, AbstractInsnNode insn, LabelMap labelMap) {
        final int opcode = insn.getOpcode();
        switch (insn.getType()) {
            case AbstractInsnNode.INSN:
                vector.putByte(opcode);
                break;
            case AbstractInsnNode.VAR_INSN: {
                final VarInsnNode varInsn = (VarInsnNode)insn;
                if (varInsn.var < 4 && opcode != Opcodes.RET) {
                    if (opcode < Opcodes.ISTORE) {
                        vector.putByte(DiffConstants.ILOAD_0 + ((opcode - Opcodes.ILOAD) << 2) + varInsn.var);
                    } else {
                        vector.putByte(DiffConstants.ISTORE_0 + ((opcode - Opcodes.ISTORE) << 2) + varInsn.var);
                    }
                } else if (varInsn.var >= 256) {
                    vector.putByte(DiffConstants.WIDE).putByte(opcode).putShort(varInsn.var);
                } else {
                    vector.putByte(opcode).putByte(varInsn.var);
                }
                break;
            }
            case AbstractInsnNode.JUMP_INSN: {
                final JumpInsnNode jumpInsn = (JumpInsnNode)insn;
                final int target = labelMap.getId(jumpInsn.label);
                if (target > 0xffff) {
                    vector.putByte(opcode - Opcodes.GOTO + DiffConstants.GOTO_W).putInt(target);
                } else {
                    vector.putByte(opcode).putShort(target);
                }
                break;
            }
            case AbstractInsnNode.TABLESWITCH_INSN: {
                final TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode)insn;
                vector.putByte(opcode);
                vector.putInt(labelMap.getId(tableSwitchInsn.dflt));
                vector.putInt(tableSwitchInsn.min);
                vector.putInt(tableSwitchInsn.max);
                for (final LabelNode label : tableSwitchInsn.labels) {
                    vector.putInt(labelMap.getId(label));
                }
                break;
            }
            case AbstractInsnNode.LOOKUPSWITCH_INSN: {
                final LookupSwitchInsnNode lookupSwitchInsn = (LookupSwitchInsnNode)insn;
                vector.putByte(opcode);
                vector.putInt(labelMap.getId(lookupSwitchInsn.dflt));
                final int numPairs = lookupSwitchInsn.keys.size();
                vector.putInt(numPairs);
                for (int i = 0; i < numPairs; i++) {
                    vector.putInt(lookupSwitchInsn.keys.get(i));
                    vector.putInt(labelMap.getId(lookupSwitchInsn.labels.get(i)));
                }
                break;
            }
            case AbstractInsnNode.INT_INSN: {
                final IntInsnNode intInsn = (IntInsnNode)insn;
                vector.putByte(opcode);
                if (opcode == Opcodes.SIPUSH) {
                    vector.putShort(intInsn.operand);
                } else {
                    vector.putByte(intInsn.operand);
                }
                break;
            }
            case AbstractInsnNode.LDC_INSN: {
                final LdcInsnNode ldcInsn = (LdcInsnNode)insn;
                final int index = symbolTable.addConstant(ldcInsn.cst).index;
                if (index > 0xff) {
                    vector.putByte(DiffConstants.LDC_W).putShort(index);
                } else {
                    vector.putByte(opcode).putByte(index);
                }
                break;
            }
            case AbstractInsnNode.FIELD_INSN: {
                final FieldInsnNode fieldInsn = (FieldInsnNode)insn;
                vector.putByte(opcode).putShort(symbolTable.addConstantFieldref(
                    fieldInsn.owner, fieldInsn.name, fieldInsn.desc
                ).index);
                break;
            }
            case AbstractInsnNode.METHOD_INSN: {
                final MethodInsnNode methodInsn = (MethodInsnNode)insn;
                vector.putByte(opcode).putShort(symbolTable.addConstantMethodref(
                    methodInsn.owner, methodInsn.name, methodInsn.desc, methodInsn.itf
                ).index);
                break;
            }
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
                final InvokeDynamicInsnNode invokeDynamicInsn = (InvokeDynamicInsnNode)insn;
                vector.putByte(opcode).putShort(symbolTable.addConstantInvokeDynamic(
                    invokeDynamicInsn.name, invokeDynamicInsn.desc, invokeDynamicInsn.bsm, invokeDynamicInsn.bsmArgs
                ).index);
                break;
            }
            case AbstractInsnNode.TYPE_INSN: {
                final TypeInsnNode typeInsn = (TypeInsnNode)insn;
                vector.putByte(opcode).putShort(symbolTable.addConstantClass(typeInsn.desc).index);
                break;
            }
            case AbstractInsnNode.IINC_INSN: {
                final IincInsnNode iincInsn = (IincInsnNode)insn;
                if (iincInsn.var > 255 || iincInsn.incr > 127 || iincInsn.incr < -128) {
                    vector.putByte(DiffConstants.WIDE).putByte(opcode).putShort(iincInsn.var).putShort(iincInsn.incr);
                } else {
                    vector.putByte(opcode).putByte(iincInsn.var).putByte(iincInsn.incr);
                }
                break;
            }
            case AbstractInsnNode.MULTIANEWARRAY_INSN: {
                final MultiANewArrayInsnNode multiANewArrayInsn = (MultiANewArrayInsnNode)insn;
                vector.putByte(opcode)
                    .putShort(symbolTable.addConstantClass(multiANewArrayInsn.desc).index)
                    .putByte(multiANewArrayInsn.dims);
                break;
            }
            case AbstractInsnNode.LABEL:
            case AbstractInsnNode.FRAME:
            case AbstractInsnNode.LINE: {
                final int specialType = insn.getType();
                vector.putByte(255).putByte(specialType);
                switch (specialType) {
                    case AbstractInsnNode.LABEL:
                        break;
                    case AbstractInsnNode.FRAME: {
                        final FrameNode frame = (FrameNode)insn;
                        vector.putByte(frame.type);
                        switch (frame.type) {
                            case Opcodes.F_NEW:
                            case Opcodes.F_FULL:
                                vector.putShort(frame.local.size());
                                vector.putShort(frame.stack.size());
                                writeFrameObjects(frame.local, vector, labelMap);
                                writeFrameObjects(frame.stack, vector, labelMap);
                                break;
                            case Opcodes.F_APPEND:
                                vector.putShort(frame.local.size());
                                writeFrameObjects(frame.local, vector, labelMap);
                                break;
                            case Opcodes.F_CHOP:
                                vector.putShort(frame.local.size());
                                break;
                            case Opcodes.F_SAME:
                                break;
                            case Opcodes.F_SAME1:
                                writeFrameObject(frame.stack.get(0), vector, labelMap);
                                break;
                            default:
                                throw new IllegalArgumentException("Unknown frame type " + frame.type);
                        }
                        break;
                    }
                    case AbstractInsnNode.LINE: {
                        final LineNumberNode lineNumber = (LineNumberNode)insn;
                        vector.putShort(lineNumber.line).putShort(labelMap.getId(lineNumber.start));
                        break;
                    }
                }
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown insn type " + insn.getType());
        }
    }

    private void writeFrameObjects(List<Object> frameObjects, ByteVector vector, LabelMap labelMap) {
        for (final Object frameObject : frameObjects) {
            writeFrameObject(frameObject, vector, labelMap);
        }
    }

    private void writeFrameObject(Object frameObject, ByteVector vector, LabelMap labelMap) {
        if (frameObject instanceof Integer) {
            vector.putByte((Integer)frameObject);
        } else if (frameObject instanceof String) {
            vector.putByte(Frame.ITEM_OBJECT).putShort(symbolTable.addConstantClass((String)frameObject).index);
        } else if (frameObject instanceof LabelNode) {
            vector.putByte(Frame.ITEM_UNINITIALIZED).putShort(labelMap.getId((LabelNode)frameObject));
        } else {
            throw new IllegalArgumentException("Unknown frame object type: " + frameObject.getClass());
        }
    }

    private void writeTypeAnnotation(ByteVector vector, TypeAnnotationNode annotation, boolean simpleTarget) {
        if (simpleTarget) {
            vector.putByte(annotation.typeRef >>> 24);
        } else {
            ReflectUtils.invokeTypeReferencePutTarget(annotation.typeRef, vector);
        }
        ReflectUtils.invokeTypePathPut(annotation.typePath, vector);
        vector.putShort(symbolTable.addConstantUtf8(annotation.desc)).putShort(0);
        annotation.accept(new AnnotationWriter(symbolTable, true, vector));
    }
}

package io.github.prcraftmc.classdiff;

import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.nothome.delta.GDiffPatcher;
import io.github.prcraftmc.classdiff.format.*;
import io.github.prcraftmc.classdiff.util.Util;
import io.github.prcraftmc.classdiff.util.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ClassPatcher extends DiffVisitor {
    private final GDiffPatcher bytePatcher = new GDiffPatcher();
    private final ClassNode node;

    /**
     * @param node The {@link ClassNode} to patch <i>in-place</i>
     */
    public ClassPatcher(ClassNode node) {
        this.node = node;
    }

    public static void patch(ClassNode node, DiffReader patch) {
        patch.accept(new ClassPatcher(node), node);
    }

    public static void patch(ClassReader reader, DiffReader patch, ClassVisitor output) {
        final ClassNode node = new ClassNode();
        reader.accept(node, 0);
        patch(node, patch);
        node.accept(output);
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
        if (classVersion != -1) {
            node.version = classVersion;
        }
        if (access != -1) {
            node.access = access;
        }
        if (name != null) {
            node.name = name;
        }
        if (signature != null) {
            node.signature = !signature.isEmpty() ? signature : null;
        }
        if (superName != null) {
            node.superName = !superName.isEmpty() ? superName : null;
        }
        if (interfaces != null) {
            if (node.interfaces == null) {
                node.interfaces = Collections.emptyList();
            }
            try {
                node.interfaces = interfaces.applyTo(node.interfaces);
            } catch (PatchFailedException e) {
                throw new UncheckedPatchFailure(e);
            }
        }
    }

    @Override
    public void visitSource(@Nullable String source, @Nullable String debug) {
        if (source != null) {
            node.sourceFile = source;
        }
        if (debug != null) {
            node.sourceDebug = debug;
        }
    }

    @Override
    public void visitInnerClasses(Patch<InnerClassNode> patch) {
        if (node.innerClasses == null) {
            node.innerClasses = Collections.emptyList();
        }
        try {
            node.innerClasses = patch.applyTo(node.innerClasses);
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }
    }

    @Override
    public void visitOuterClass(@Nullable String className, @Nullable String methodName, @Nullable String methodDescriptor) {
        if (className != null) {
            node.outerClass = className;
        }
        if (methodName != null) {
            node.outerMethod = !methodName.isEmpty() ? methodName : null;
        }
        if (methodDescriptor != null) {
            node.outerMethodDesc = !methodDescriptor.isEmpty() ? methodDescriptor : null;
        }
    }

    @Override
    public void visitNestHost(@Nullable String nestHost) {
        node.nestHostClass = nestHost;
    }

    @Override
    public void visitNestMembers(Patch<String> patch) {
        if (node.nestMembers == null) {
            node.nestMembers = Collections.emptyList();
        }
        try {
            node.nestMembers = patch.applyTo(node.nestMembers);
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }
    }

    @Override
    public void visitPermittedSubclasses(Patch<String> patch) {
        if (node.permittedSubclasses == null) {
            node.permittedSubclasses = Collections.emptyList();
        }
        try {
            node.permittedSubclasses = patch.applyTo(node.permittedSubclasses);
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }
    }

    @Override
    public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
        try {
            if (visible) {
                if (node.visibleAnnotations == null) {
                    node.visibleAnnotations = Collections.emptyList();
                }
                node.visibleAnnotations = patch.applyTo(node.visibleAnnotations);
            } else {
                if (node.invisibleAnnotations == null) {
                    node.invisibleAnnotations = Collections.emptyList();
                }
                node.invisibleAnnotations = patch.applyTo(node.invisibleAnnotations);
            }
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }
    }

    @Override
    public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
        try {
            if (visible) {
                if (node.visibleTypeAnnotations == null) {
                    node.visibleTypeAnnotations = Collections.emptyList();
                }
                node.visibleTypeAnnotations = patch.applyTo(node.visibleTypeAnnotations);
            } else {
                if (node.invisibleTypeAnnotations == null) {
                    node.invisibleTypeAnnotations = Collections.emptyList();
                }
                node.invisibleTypeAnnotations = patch.applyTo(node.invisibleTypeAnnotations);
            }
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }
    }

    @Override
    public void visitRecordComponents(Patch<MemberName> patch) {
        if (patch.getDeltas().isEmpty()) return;

        final List<MemberName> originalList = MemberName.fromRecordComponents(node.recordComponents);
        final List<MemberName> modifiedList;
        try {
            modifiedList = patch.applyTo(originalList);
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }

        final Map<MemberName, RecordComponentNode> originalMap = new HashMap<>();
        for (int i = 0; i < originalList.size(); i++) {
            originalMap.put(originalList.get(i), node.recordComponents.get(i));
        }

        node.recordComponents = new ArrayList<>();
        for (final MemberName name : modifiedList) {
            RecordComponentNode recordNode = originalMap.get(name);
            if (recordNode == null) {
                recordNode = new RecordComponentNode(name.name, name.descriptor, null);
            }
            node.recordComponents.add(recordNode);
        }
    }

    @Nullable
    @Override
    public RecordComponentDiffVisitor visitRecordComponent(String name, String descriptor, @Nullable String signature) {
        if (node.recordComponents == null) {
            node.recordComponents = new ArrayList<>();
        }

        RecordComponentNode recordNode = null;
        for (final RecordComponentNode test : node.recordComponents) {
            if (test.name.equals(name) && test.descriptor.equals(descriptor)) {
                recordNode = test;
                break;
            }
        }
        if (recordNode == null) {
            recordNode = new RecordComponentNode(name, descriptor, signature);
            node.recordComponents.add(recordNode);
        }

        recordNode.signature = signature;
        final RecordComponentNode fRecordNode = recordNode;
        return new RecordComponentDiffVisitor() {
            @Override
            public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
                try {
                    if (visible) {
                        if (fRecordNode.visibleAnnotations == null) {
                            fRecordNode.visibleAnnotations = Collections.emptyList();
                        }
                        fRecordNode.visibleAnnotations = patch.applyTo(fRecordNode.visibleAnnotations);
                    } else {
                        if (fRecordNode.invisibleAnnotations == null) {
                            fRecordNode.invisibleAnnotations = Collections.emptyList();
                        }
                        fRecordNode.invisibleAnnotations = patch.applyTo(fRecordNode.invisibleAnnotations);
                    }
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
                try {
                    if (visible) {
                        if (fRecordNode.visibleTypeAnnotations == null) {
                            fRecordNode.visibleTypeAnnotations = Collections.emptyList();
                        }
                        fRecordNode.visibleTypeAnnotations = patch.applyTo(fRecordNode.visibleTypeAnnotations);
                    } else {
                        if (fRecordNode.invisibleTypeAnnotations == null) {
                            fRecordNode.invisibleTypeAnnotations = Collections.emptyList();
                        }
                        fRecordNode.invisibleTypeAnnotations = patch.applyTo(fRecordNode.invisibleTypeAnnotations);
                    }
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
                if (patchOrContents == null) {
                    fRecordNode.attrs.removeIf(attr -> attr.type.equals(name));
                    return;
                }
                for (final Attribute attr : fRecordNode.attrs) {
                    if (attr.type.equals(name)) {
                        final byte[] original = ReflectUtils.getAttributeContent(attr);
                        final byte[] patched;
                        try {
                            patched = bytePatcher.patch(original, patchOrContents);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        ReflectUtils.setAttributeContent(attr, patched);
                        return;
                    }
                }
                final Attribute attr = ReflectUtils.newAttribute(name);
                ReflectUtils.setAttributeContent(attr, patchOrContents);
                fRecordNode.attrs.add(attr);
            }
        };
    }

    @Override
    public ModuleDiffVisitor visitModule(@Nullable String name, int access, @Nullable String version) {
        if (name == null) {
            // Remove the module!
            node.module = null;
            return null;
        }
        if (node.module == null) {
            node.module = new ModuleNode(name, access, version);
        } else {
            node.module.name = name;
            node.module.access = access;
            node.module.version = version;
        }

        return new ModuleDiffVisitor() {
            @Override
            public void visitMainClass(@Nullable String mainClass) {
                node.module.mainClass = mainClass;
            }

            @Override
            public void visitPackages(Patch<String> patch) {
                if (node.module.packages == null) {
                    node.module.packages = Collections.emptyList();
                }
                try {
                    node.module.packages = patch.applyTo(node.module.packages);
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitRequires(Patch<ModuleRequireNode> patch) {
                if (node.module.requires == null) {
                    node.module.requires = Collections.emptyList();
                }
                try {
                    node.module.requires = patch.applyTo(node.module.requires);
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitExports(Patch<ModuleExportNode> patch) {
                if (node.module.exports == null) {
                    node.module.exports = Collections.emptyList();
                }
                try {
                    node.module.exports = patch.applyTo(node.module.exports);
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitOpens(Patch<ModuleOpenNode> patch) {
                if (node.module.opens == null) {
                    node.module.opens = Collections.emptyList();
                }
                try {
                    node.module.opens = patch.applyTo(node.module.opens);
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitUses(Patch<String> patch) {
                if (node.module.uses == null) {
                    node.module.uses = Collections.emptyList();
                }
                try {
                    node.module.uses = patch.applyTo(node.module.uses);
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitProvides(Patch<ModuleProvideNode> patch) {
                if (node.module.provides == null) {
                    node.module.provides = Collections.emptyList();
                }
                try {
                    node.module.provides = patch.applyTo(node.module.provides);
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }
        };
    }

    @Override
    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        if (patchOrContents == null) {
            node.attrs.removeIf(attr -> attr.type.equals(name));
            return;
        }
        for (final Attribute attr : node.attrs) {
            if (attr.type.equals(name)) {
                final byte[] original = ReflectUtils.getAttributeContent(attr);
                final byte[] patched;
                try {
                    patched = bytePatcher.patch(original, patchOrContents);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                ReflectUtils.setAttributeContent(attr, patched);
                return;
            }
        }
        final Attribute attr = ReflectUtils.newAttribute(name);
        ReflectUtils.setAttributeContent(attr, patchOrContents);
        node.attrs.add(attr);
    }

    @Override
    public void visitFields(Patch<MemberName> patch) {
        if (patch.getDeltas().isEmpty()) return;

        final List<MemberName> originalList = MemberName.fromFields(node.fields);
        final List<MemberName> modifiedList;
        try {
            modifiedList = patch.applyTo(originalList);
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }

        final Map<MemberName, FieldNode> originalMap = new HashMap<>();
        for (int i = 0; i < originalList.size(); i++) {
            originalMap.put(originalList.get(i), node.fields.get(i));
        }

        node.fields = new ArrayList<>();
        for (final MemberName name : modifiedList) {
            FieldNode fieldNode = originalMap.get(name);
            if (fieldNode == null) {
                fieldNode = new FieldNode(0, name.name, name.descriptor, null, null);
            }
            node.fields.add(fieldNode);
        }
    }

    @Nullable
    @Override
    public FieldDiffVisitor visitField(
        int access,
        String name,
        String descriptor,
        @Nullable String signature,
        @Nullable Object value
    ) {
        if (node.fields == null) {
            node.fields = new ArrayList<>();
        }

        FieldNode fieldNode = null;
        for (final FieldNode test : node.fields) {
            if (test.name.equals(name) && test.desc.equals(descriptor)) {
                fieldNode = test;
                break;
            }
        }
        if (fieldNode == null) {
            fieldNode = new FieldNode(access, name, descriptor, signature, value);
            node.fields.add(fieldNode);
        }

        fieldNode.access = access;
        fieldNode.signature = signature;
        fieldNode.value = value;

        final FieldNode fFieldNode = fieldNode;
        return new FieldDiffVisitor() {
            @Override
            public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
                try {
                    if (visible) {
                        if (fFieldNode.visibleAnnotations == null) {
                            fFieldNode.visibleAnnotations = Collections.emptyList();
                        }
                        fFieldNode.visibleAnnotations = patch.applyTo(fFieldNode.visibleAnnotations);
                    } else {
                        if (fFieldNode.invisibleAnnotations == null) {
                            fFieldNode.invisibleAnnotations = Collections.emptyList();
                        }
                        fFieldNode.invisibleAnnotations = patch.applyTo(fFieldNode.invisibleAnnotations);
                    }
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
                try {
                    if (visible) {
                        if (fFieldNode.visibleTypeAnnotations == null) {
                            fFieldNode.visibleTypeAnnotations = Collections.emptyList();
                        }
                        fFieldNode.visibleTypeAnnotations = patch.applyTo(fFieldNode.visibleTypeAnnotations);
                    } else {
                        if (fFieldNode.invisibleTypeAnnotations == null) {
                            fFieldNode.invisibleTypeAnnotations = Collections.emptyList();
                        }
                        fFieldNode.invisibleTypeAnnotations = patch.applyTo(fFieldNode.invisibleTypeAnnotations);
                    }
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
                if (patchOrContents == null) {
                    fFieldNode.attrs.removeIf(attr -> attr.type.equals(name));
                    return;
                }
                for (final Attribute attr : fFieldNode.attrs) {
                    if (attr.type.equals(name)) {
                        final byte[] original = ReflectUtils.getAttributeContent(attr);
                        final byte[] patched;
                        try {
                            patched = bytePatcher.patch(original, patchOrContents);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        ReflectUtils.setAttributeContent(attr, patched);
                        return;
                    }
                }
                final Attribute attr = ReflectUtils.newAttribute(name);
                ReflectUtils.setAttributeContent(attr, patchOrContents);
                fFieldNode.attrs.add(attr);
            }
        };
    }

    @Override
    public void visitMethods(Patch<MemberName> patch) {
        if (patch.getDeltas().isEmpty()) return;

        final List<MemberName> originalList = MemberName.fromMethods(node.methods);
        final List<MemberName> modifiedList;
        try {
            modifiedList = patch.applyTo(originalList);
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }

        final Map<MemberName, MethodNode> originalMap = new HashMap<>();
        for (int i = 0; i < originalList.size(); i++) {
            originalMap.put(originalList.get(i), node.methods.get(i));
        }

        node.methods = new ArrayList<>();
        for (final MemberName name : modifiedList) {
            MethodNode methodNode = originalMap.get(name);
            if (methodNode == null) {
                methodNode = new MethodNode(0, name.name, name.descriptor, null, null);
            }
            node.methods.add(methodNode);
        }
    }

    @Nullable
    @Override
    public MethodDiffVisitor visitMethod(
        int access,
        String name,
        String descriptor,
        @Nullable String signature,
        Patch<String> exceptions
    ) {
        if (node.methods == null) {
            node.methods = new ArrayList<>();
        }

        MethodNode methodNode = null;
        for (final MethodNode test : node.methods) {
            if (test.name.equals(name) && test.desc.equals(descriptor)) {
                methodNode = test;
                break;
            }
        }
        if (methodNode == null) {
            methodNode = new MethodNode(access, name, descriptor, signature, null);
            node.methods.add(methodNode);
        }

        methodNode.access = access;
        methodNode.signature = signature;
        try {
            methodNode.exceptions = exceptions.applyTo(methodNode.exceptions);
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }

        final MethodNode fMethodNode = methodNode;
        return new MethodDiffVisitor() {
            boolean insnsFrozen;
            LabelMap insnsLabelMap;

            @Override
            public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
                try {
                    if (visible) {
                        if (fMethodNode.visibleAnnotations == null) {
                            fMethodNode.visibleAnnotations = Collections.emptyList();
                        }
                        fMethodNode.visibleAnnotations = patch.applyTo(fMethodNode.visibleAnnotations);
                    } else {
                        if (fMethodNode.invisibleAnnotations == null) {
                            fMethodNode.invisibleAnnotations = Collections.emptyList();
                        }
                        fMethodNode.invisibleAnnotations = patch.applyTo(fMethodNode.invisibleAnnotations);
                    }
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
                try {
                    if (visible) {
                        if (fMethodNode.visibleTypeAnnotations == null) {
                            fMethodNode.visibleTypeAnnotations = Collections.emptyList();
                        }
                        fMethodNode.visibleTypeAnnotations = patch.applyTo(fMethodNode.visibleTypeAnnotations);
                    } else {
                        if (fMethodNode.invisibleTypeAnnotations == null) {
                            fMethodNode.invisibleTypeAnnotations = Collections.emptyList();
                        }
                        fMethodNode.invisibleTypeAnnotations = patch.applyTo(fMethodNode.invisibleTypeAnnotations);
                    }
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitAnnotationDefault(@Nullable Object value) {
                fMethodNode.annotationDefault = value;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void visitParameterAnnotations(int annotableCount, List<Patch<AnnotationNode>> patches, boolean visible) {
                final List<AnnotationNode>[] output;
                if (visible) {
                    fMethodNode.visibleAnnotableParameterCount = annotableCount;
                    if (fMethodNode.visibleParameterAnnotations == null) {
                        fMethodNode.visibleParameterAnnotations = (List<AnnotationNode>[])new List<?>[patches.size()];
                    }
                    output = fMethodNode.visibleParameterAnnotations;
                } else {
                    fMethodNode.invisibleAnnotableParameterCount = annotableCount;
                    if (fMethodNode.invisibleParameterAnnotations == null) {
                        fMethodNode.invisibleParameterAnnotations = (List<AnnotationNode>[])new List<?>[patches.size()];
                    }
                    output = fMethodNode.invisibleParameterAnnotations;
                }
                try {
                    for (int i = 0; i < output.length; i++) {
                        output[i] = patches.get(i).applyTo(Util.getListFromArray(output, i));
                    }
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitParameters(Patch<ParameterNode> parameters) {
                if (fMethodNode.parameters == null) {
                    fMethodNode.parameters = Collections.emptyList();
                }
                try {
                    fMethodNode.parameters = parameters.applyTo(fMethodNode.parameters);
                } catch (PatchFailedException e) {
                    throw new UncheckedPatchFailure(e);
                }
            }

            @Override
            public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
                if (patchOrContents == null) {
                    fMethodNode.attrs.removeIf(attr -> attr.type.equals(name));
                    return;
                }
                for (final Attribute attr : fMethodNode.attrs) {
                    if (attr.type.equals(name)) {
                        final byte[] original = ReflectUtils.getAttributeContent(attr);
                        final byte[] patched;
                        try {
                            patched = bytePatcher.patch(original, patchOrContents);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        ReflectUtils.setAttributeContent(attr, patched);
                        return;
                    }
                }
                final Attribute attr = ReflectUtils.newAttribute(name);
                ReflectUtils.setAttributeContent(attr, patchOrContents);
                fMethodNode.attrs.add(attr);
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                fMethodNode.maxStack = maxStack;
                fMethodNode.maxLocals = maxLocals;
            }

            @Override
            public void visitInsns(Patch<AbstractInsnNode> patch, Supplier<LabelMap> patchedLabelMap) {
                if (insnsFrozen) {
                    throw new IllegalStateException("Cannot call ClassPatcher.visitMethod().visitInsns() after freeze");
                }

                final Map<LabelNode, LabelNode> clonedLabels = new HashMap<>();
                for (final AbstractInsnNode insn : fMethodNode.instructions) {
                    if (insn instanceof LabelNode) {
                        clonedLabels.put((LabelNode)insn, new LabelNode());
                    }
                }

                final List<AbstractInsnNode> clonedInsns = new ArrayList<>(fMethodNode.instructions.size());
                for (final AbstractInsnNode insn : fMethodNode.instructions) {
                    clonedInsns.add(insn.clone(clonedLabels));
                }

                final InsnList newInsns = Util.asInsnList(Util.applyPatchUnchecked(patch, clonedInsns));

                final LabelMap newLabelMap = new LabelMap(newInsns);
                insnsLabelMap = newLabelMap;
                for (final AbstractInsnNode insn : newInsns) {
                    switch (insn.getType()) {
                        case AbstractInsnNode.JUMP_INSN: {
                            final JumpInsnNode jumpInsn = (JumpInsnNode)insn;
                            jumpInsn.label = newLabelMap.resolve(jumpInsn.label);
                            break;
                        }
                        case AbstractInsnNode.TABLESWITCH_INSN: {
                            final TableSwitchInsnNode tableSwitchInsn = (TableSwitchInsnNode)insn;
                            tableSwitchInsn.dflt = newLabelMap.resolve(tableSwitchInsn.dflt);
                            tableSwitchInsn.labels.replaceAll(newLabelMap::resolve);
                            break;
                        }
                        case AbstractInsnNode.LOOKUPSWITCH_INSN: {
                            final LookupSwitchInsnNode lookupSwitchInsn = (LookupSwitchInsnNode)insn;
                            lookupSwitchInsn.dflt = newLabelMap.resolve(lookupSwitchInsn.dflt);
                            lookupSwitchInsn.labels.replaceAll(newLabelMap::resolve);
                            break;
                        }
                        case AbstractInsnNode.LINE: {
                            final LineNumberNode lineNumber = (LineNumberNode)insn;
                            lineNumber.start = newLabelMap.resolve(lineNumber.start);
                            break;
                        }
                        case AbstractInsnNode.FRAME: {
                            final FrameNode frame = (FrameNode)insn;
                            if (frame.stack != null || frame.local != null) {
                                final UnaryOperator<Object> replacer =
                                    o -> o instanceof LabelNode ? newLabelMap.resolve((LabelNode)o) : o;
                                if (frame.stack != null) {
                                    frame.stack.replaceAll(replacer);
                                }
                                if (frame.local != null) {
                                    frame.local.replaceAll(replacer);
                                }
                            }
                            break;
                        }
                    }
                }

                fMethodNode.instructions = newInsns;
            }

            @Override
            public void visitLocalVariables(List<LocalVariableNode> newLocals, @Nullable LabelMap useMap) {
                if (useMap == null) {
                    useMap = insnsLabelMap;
                }
                if (useMap == null) {
                    if (newLocals.stream().anyMatch(
                        l -> l.start instanceof SyntheticLabelNode || l.end instanceof SyntheticLabelNode
                    )) {
                        insnsFrozen = true;
                        useMap = new LabelMap(fMethodNode.instructions);
                    } else {
                        useMap = new LabelMap();
                    }
                }

                final List<LocalVariableNode> output = new ArrayList<>(newLocals.size());
                for (final LocalVariableNode local : newLocals) {
                    output.add(new LocalVariableNode(
                        local.name,
                        local.desc,
                        local.signature,
                        useMap.resolve(local.start),
                        useMap.resolve(local.end),
                        local.index
                    ));
                }
                fMethodNode.localVariables = output;
            }

            @Override
            public void visitTryCatchBlocks(List<TryCatchBlockNode> newBlocks, @Nullable LabelMap useMap) {
                if (useMap == null) {
                    useMap = insnsLabelMap;
                }
                if (useMap == null) {
                    if (newBlocks.stream().anyMatch(
                        l -> l.start instanceof SyntheticLabelNode || l.end instanceof SyntheticLabelNode || l.handler instanceof SyntheticLabelNode
                    )) {
                        insnsFrozen = true;
                        useMap = new LabelMap(fMethodNode.instructions);
                    } else {
                        useMap = new LabelMap();
                    }
                }

                final List<TryCatchBlockNode> output = new ArrayList<>(newBlocks.size());
                for (final TryCatchBlockNode block : newBlocks) {
                    final TryCatchBlockNode newBlock = new TryCatchBlockNode(
                        useMap.resolve(block.start),
                        useMap.resolve(block.end),
                        useMap.resolve(block.handler),
                        block.type
                    );
                    newBlock.invisibleTypeAnnotations = block.invisibleTypeAnnotations;
                    newBlock.visibleTypeAnnotations = block.visibleTypeAnnotations;
                    output.add(newBlock);
                }
                fMethodNode.tryCatchBlocks = output;
            }
        };
    }
}

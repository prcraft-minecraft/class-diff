package io.github.prcraftmc.classdiff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.nothome.delta.Delta;
import io.github.prcraftmc.classdiff.format.*;
import io.github.prcraftmc.classdiff.util.Util;
import io.github.prcraftmc.classdiff.util.*;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

public class ClassDiffer {
    private final Delta delta = new Delta();
    private final DiffVisitor output;

    public ClassDiffer(DiffVisitor output) {
        this.output = output;
    }

    public static void diff(ClassNode original, ClassNode modified, DiffVisitor result) {
        new ClassDiffer(result).accept(original, modified);
    }

    public static void diff(ClassReader original, ClassReader modified, DiffVisitor result) {
        final ClassNode aNode = new ClassNode();
        original.accept(aNode, 0);
        final ClassNode bNode = new ClassNode();
        modified.accept(bNode, 0);
        diff(aNode, bNode, result);
    }

    public void accept(ClassNode original, ClassNode modified) {
        final Patch<String> interfacePatch;
        if (Objects.equals(original.interfaces, modified.interfaces)) {
            interfacePatch = null;
        } else {
            interfacePatch = DiffUtils.diff(
                Util.nullToEmpty(original.interfaces),
                Util.nullToEmpty(modified.interfaces)
            );
        }

        output.visit(
            DiffConstants.V1,
            modified.version == original.version ? -1 : modified.version,
            modified.access == original.access ? -1 : modified.access,
            modified.name.equals(original.name) ? null : modified.name,
            Objects.equals(modified.signature, original.signature) ? null : (modified.signature != null ? modified.signature : ""),
            Objects.equals(modified.superName, original.superName) ? null : (modified.superName != null ? modified.superName : ""),
            interfacePatch
        );

        if (
            !Objects.equals(original.sourceFile, modified.sourceFile) ||
                !Objects.equals(original.sourceDebug, modified.sourceDebug)
        ) {
            output.visitSource(
                Objects.equals(original.sourceFile, modified.sourceFile) ? null : modified.sourceFile,
                Objects.equals(original.sourceDebug, modified.sourceDebug) ? null : modified.sourceDebug
            );
        }

        if (!Equalizers.listEquals(original.innerClasses, modified.innerClasses, Equalizers::innerClass)) {
            output.visitInnerClasses(DiffUtils.diff(
                Util.nullToEmpty(original.innerClasses),
                Util.nullToEmpty(modified.innerClasses),
                Equalizers::innerClass
            ));
        }

        if (
            !Objects.equals(original.outerClass, modified.outerClass) ||
                !Objects.equals(original.outerMethod, modified.outerMethod) ||
                !Objects.equals(original.outerMethodDesc, modified.outerMethodDesc)
        ) {
            output.visitOuterClass(
                Objects.equals(original.outerClass, modified.outerClass)
                    ? null : modified.outerClass,
                Objects.equals(original.outerMethod, modified.outerMethod)
                    ? null : (modified.outerMethod == null ? "" : modified.outerMethod),
                Objects.equals(original.outerMethodDesc, modified.outerMethodDesc)
                    ? null : (modified.outerMethodDesc == null ? "" : modified.outerMethodDesc)
            );
        }

        if (!Objects.equals(original.nestHostClass, modified.nestHostClass)) {
            output.visitNestHost(modified.nestHostClass);
        }

        if (!Objects.equals(original.nestMembers, modified.nestMembers)) {
            output.visitNestMembers(DiffUtils.diff(
                Util.nullToEmpty(original.nestMembers),
                Util.nullToEmpty(modified.nestMembers)
            ));
        }

        if (!Objects.equals(original.permittedSubclasses, modified.permittedSubclasses)) {
            output.visitPermittedSubclasses(DiffUtils.diff(
                Util.nullToEmpty(original.permittedSubclasses),
                Util.nullToEmpty(modified.permittedSubclasses)
            ));
        }

        diffAnnotated(
            output,
            original.visibleAnnotations, modified.visibleAnnotations,
            original.invisibleAnnotations, modified.invisibleAnnotations,
            original.visibleTypeAnnotations, modified.visibleTypeAnnotations,
            original.invisibleTypeAnnotations, modified.invisibleTypeAnnotations
        );

        diffRecordComponents(original, modified);

        if (!Equalizers.module(original.module, modified.module)) {
            diffModules(original.module, modified.module);
        }

        diffAttributable(output, original.attrs, modified.attrs);

        diffFields(original, modified);

        diffMethods(original, modified);

        output.visitEnd();
    }

    private void diffRecordComponents(ClassNode original, ClassNode modified) {
        final List<MemberName> aComponents = MemberName.fromRecordComponents(original.recordComponents);
        final List<MemberName> bComponents = MemberName.fromRecordComponents(modified.recordComponents);
        if (!aComponents.equals(bComponents)) {
            output.visitRecordComponents(DiffUtils.diff(aComponents, bComponents));
        }
        if (!bComponents.isEmpty()) {
            final Map<MemberName, RecordComponentNode> bMap = new LinkedHashMap<>();
            for (int i = 0; i < bComponents.size(); i++) {
                bMap.put(bComponents.get(i), modified.recordComponents.get(i));
            }

            final Set<MemberName> extra = new LinkedHashSet<>(bMap.keySet());
            for (int i = 0; i < aComponents.size(); i++) {
                final MemberName name = aComponents.get(i);
                if (extra.remove(name)) {
                    final RecordComponentNode aNode = original.recordComponents.get(i);
                    final RecordComponentNode bNode = bMap.get(name);
                    if (!Equalizers.recordComponent(aNode, bNode)) {
                        final RecordComponentDiffVisitor visitor = output.visitRecordComponent(
                            name.name, name.descriptor, bNode.signature
                        );
                        if (visitor != null) {
                            diffRecordComponents(aNode, bNode, visitor);
                        }
                    }
                }
            }

            for (final MemberName name : extra) {
                final RecordComponentNode node = bMap.get(name);
                final RecordComponentDiffVisitor visitor = output.visitRecordComponent(
                    name.name, name.descriptor, node.signature
                );
                if (visitor != null) {
                    if (node.visibleAnnotations != null) {
                        visitor.visitAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.visibleAnnotations, Equalizers::annotation
                        ), true);
                    }
                    if (node.invisibleAnnotations != null) {
                        visitor.visitAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.invisibleAnnotations, Equalizers::annotation
                        ), false);
                    }
                    if (node.visibleTypeAnnotations != null) {
                        visitor.visitTypeAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.visibleTypeAnnotations, Equalizers::typeAnnotation
                        ), true);
                    }
                    if (node.invisibleTypeAnnotations != null) {
                        visitor.visitTypeAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.invisibleTypeAnnotations, Equalizers::typeAnnotation
                        ), false);
                    }
                    if (node.attrs != null) {
                        for (final Attribute attr : node.attrs) {
                            visitor.visitCustomAttribute(attr.type, ReflectUtils.getAttributeContent(attr));
                        }
                    }
                    visitor.visitEnd();
                }
            }
        }
    }

    private void diffRecordComponents(
        RecordComponentNode original,
        RecordComponentNode modified,
        RecordComponentDiffVisitor output
    ) {
        diffAnnotated(
            output,
            original.visibleAnnotations, modified.visibleAnnotations,
            original.invisibleAnnotations, modified.invisibleAnnotations,
            original.visibleTypeAnnotations, modified.visibleTypeAnnotations,
            original.invisibleTypeAnnotations, modified.invisibleTypeAnnotations
        );

        diffAttributable(output, original.attrs, modified.attrs);

        output.visitEnd();
    }

    private void diffModules(ModuleNode aModule, ModuleNode bModule) {
        if (bModule == null) {
            final ModuleDiffVisitor moduleOut = output.visitModule(null, 0, null);
            if (moduleOut != null) {
                moduleOut.visitEnd();
            }
            return;
        }

        if (aModule == null) {
            aModule = new ModuleNode("", 0, null);
        }

        final ModuleDiffVisitor moduleOut = output.visitModule(bModule.name, bModule.access, bModule.version);
        if (moduleOut != null) {
            if (!Objects.equals(aModule.mainClass, bModule.mainClass)) {
                moduleOut.visitMainClass(bModule.mainClass);
            }

            if (!Objects.equals(aModule.packages, bModule.packages)) {
                moduleOut.visitPackages(DiffUtils.diff(
                    Util.nullToEmpty(aModule.packages),
                    Util.nullToEmpty(bModule.packages)
                ));
            }

            if (!Equalizers.listEquals(aModule.requires, bModule.requires, Equalizers::moduleRequire)) {
                moduleOut.visitRequires(DiffUtils.diff(
                    Util.nullToEmpty(aModule.requires),
                    Util.nullToEmpty(bModule.requires),
                    Equalizers::moduleRequire
                ));
            }

            if (!Equalizers.listEquals(aModule.exports, bModule.exports, Equalizers::moduleExport)) {
                moduleOut.visitExports(DiffUtils.diff(
                    Util.nullToEmpty(aModule.exports),
                    Util.nullToEmpty(bModule.exports),
                    Equalizers::moduleExport
                ));
            }

            if (!Equalizers.listEquals(aModule.opens, bModule.opens, Equalizers::moduleOpen)) {
                moduleOut.visitOpens(DiffUtils.diff(
                    Util.nullToEmpty(aModule.opens),
                    Util.nullToEmpty(bModule.opens),
                    Equalizers::moduleOpen
                ));
            }

            if (!Objects.equals(aModule.packages, bModule.packages)) {
                moduleOut.visitUses(DiffUtils.diff(
                    Util.nullToEmpty(aModule.uses),
                    Util.nullToEmpty(bModule.uses)
                ));
            }

            if (!Equalizers.listEquals(aModule.provides, bModule.provides, Equalizers::moduleProvide)) {
                moduleOut.visitProvides(DiffUtils.diff(
                    Util.nullToEmpty(aModule.provides),
                    Util.nullToEmpty(bModule.provides),
                    Equalizers::moduleProvide
                ));
            }

            moduleOut.visitEnd();
        }
    }

    private void diffFields(ClassNode original, ClassNode modified) {
        final List<MemberName> aFields = MemberName.fromFields(original.fields);
        final List<MemberName> bFields = MemberName.fromFields(modified.fields);
        if (!aFields.equals(bFields)) {
            output.visitFields(DiffUtils.diff(aFields, bFields));
        }
        if (!bFields.isEmpty()) {
            final Map<MemberName, FieldNode> bMap = new LinkedHashMap<>();
            for (int i = 0; i < bFields.size(); i++) {
                bMap.put(bFields.get(i), modified.fields.get(i));
            }

            final Set<MemberName> extra = new LinkedHashSet<>(bMap.keySet());
            for (int i = 0; i < aFields.size(); i++) {
                final MemberName name = aFields.get(i);
                if (extra.remove(name)) {
                    final FieldNode aNode = original.fields.get(i);
                    final FieldNode bNode = bMap.get(name);
                    if (!Equalizers.field(aNode, bNode)) {
                        final FieldDiffVisitor visitor = output.visitField(
                            bNode.access, name.name, name.descriptor, bNode.signature, bNode.value
                        );
                        if (visitor != null) {
                            diffFields(aNode, bNode, visitor);
                        }
                    }
                }
            }

            for (final MemberName name : extra) {
                final FieldNode node = bMap.get(name);
                final FieldDiffVisitor visitor = output.visitField(
                    node.access, name.name, name.descriptor, node.signature, node.value
                );
                if (visitor != null) {
                    if (node.visibleAnnotations != null) {
                        visitor.visitAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.visibleAnnotations, Equalizers::annotation
                        ), true);
                    }
                    if (node.invisibleAnnotations != null) {
                        visitor.visitAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.invisibleAnnotations, Equalizers::annotation
                        ), false);
                    }
                    if (node.visibleTypeAnnotations != null) {
                        visitor.visitTypeAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.visibleTypeAnnotations, Equalizers::typeAnnotation
                        ), true);
                    }
                    if (node.invisibleTypeAnnotations != null) {
                        visitor.visitTypeAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.invisibleTypeAnnotations, Equalizers::typeAnnotation
                        ), false);
                    }
                    if (node.attrs != null) {
                        for (final Attribute attr : node.attrs) {
                            visitor.visitCustomAttribute(attr.type, ReflectUtils.getAttributeContent(attr));
                        }
                    }
                    visitor.visitEnd();
                }
            }
        }
    }

    private void diffFields(
        FieldNode original,
        FieldNode modified,
        FieldDiffVisitor output
    ) {
        diffAnnotated(
            output,
            original.visibleAnnotations, modified.visibleAnnotations,
            original.invisibleAnnotations, modified.invisibleAnnotations,
            original.visibleTypeAnnotations, modified.visibleTypeAnnotations,
            original.invisibleTypeAnnotations, modified.invisibleTypeAnnotations
        );

        diffAttributable(output, original.attrs, modified.attrs);

        output.visitEnd();
    }

    private void diffMethods(ClassNode original, ClassNode modified) {
        final List<MemberName> aMethods = MemberName.fromMethods(original.methods);
        final List<MemberName> bMethods = MemberName.fromMethods(modified.methods);
        if (!aMethods.equals(bMethods)) {
            output.visitMethods(DiffUtils.diff(aMethods, bMethods));
        }
        if (!bMethods.isEmpty()) {
            final Map<MemberName, MethodNode> bMap = new LinkedHashMap<>();
            for (int i = 0; i < bMethods.size(); i++) {
                bMap.put(bMethods.get(i), modified.methods.get(i));
            }

            final Set<MemberName> extra = new LinkedHashSet<>(bMap.keySet());
            for (int i = 0; i < aMethods.size(); i++) {
                final MemberName name = aMethods.get(i);
                if (extra.remove(name)) {
                    final MethodNode aNode = original.methods.get(i);
                    final MethodNode bNode = bMap.get(name);
                    if (!Equalizers.method(aNode, bNode)) {
                        final MethodDiffVisitor visitor = output.visitMethod(
                            bNode.access, name.name, name.descriptor, bNode.signature,
                            DiffUtils.diff(aNode.exceptions, bNode.exceptions)
                        );
                        if (visitor != null) {
                            diffMethods(aNode, bNode, visitor);
                        }
                    }
                }
            }

            for (final MemberName name : extra) {
                final MethodNode node = bMap.get(name);
                final MethodDiffVisitor visitor = output.visitMethod(
                    node.access, name.name, name.descriptor, node.signature,
                    DiffUtils.diff(Collections.emptyList(), node.exceptions)
                );
                if (visitor != null) {
                    if (node.visibleAnnotations != null) {
                        visitor.visitAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.visibleAnnotations, Equalizers::annotation
                        ), true);
                    }
                    if (node.invisibleAnnotations != null) {
                        visitor.visitAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.invisibleAnnotations, Equalizers::annotation
                        ), false);
                    }
                    if (node.visibleTypeAnnotations != null) {
                        visitor.visitTypeAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.visibleTypeAnnotations, Equalizers::typeAnnotation
                        ), true);
                    }
                    if (node.invisibleTypeAnnotations != null) {
                        visitor.visitTypeAnnotations(DiffUtils.diff(
                            Collections.emptyList(), node.invisibleTypeAnnotations, Equalizers::typeAnnotation
                        ), false);
                    }
                    if (node.annotationDefault != null) {
                        visitor.visitAnnotationDefault(node.annotationDefault);
                    }
                    if (node.visibleAnnotableParameterCount != 0 || node.visibleParameterAnnotations != null) {
                        final int paramCount = Type.getArgumentTypes(node.desc).length;
                        final List<Patch<AnnotationNode>> patches = new ArrayList<>(paramCount);
                        for (int i = 0; i < paramCount; i++) {
                            patches.add(DiffUtils.diff(
                                Collections.emptyList(),
                                Util.getListFromArray(node.visibleParameterAnnotations, i),
                                Equalizers::annotation
                            ));
                        }
                        visitor.visitParameterAnnotations(node.visibleAnnotableParameterCount, patches, true);
                    }
                    if (node.invisibleAnnotableParameterCount != 0 || node.invisibleParameterAnnotations != null) {
                        final int paramCount = Type.getArgumentTypes(node.desc).length;
                        final List<Patch<AnnotationNode>> patches = new ArrayList<>(paramCount);
                        for (int i = 0; i < paramCount; i++) {
                            patches.add(DiffUtils.diff(
                                Collections.emptyList(),
                                Util.getListFromArray(node.invisibleParameterAnnotations, i),
                                Equalizers::annotation
                            ));
                        }
                        visitor.visitParameterAnnotations(node.invisibleAnnotableParameterCount, patches, false);
                    }
                    if (node.parameters != null) {
                        visitor.visitParameters(DiffUtils.diff(
                            Collections.emptyList(), node.parameters, Equalizers::parameter
                        ));
                    }
                    if (node.attrs != null) {
                        for (final Attribute attr : node.attrs) {
                            visitor.visitCustomAttribute(attr.type, ReflectUtils.getAttributeContent(attr));
                        }
                    }
                    if (node.maxStack != 0 || node.maxLocals != 0) {
                        visitor.visitMaxs(node.maxStack, node.maxLocals);
                    }
                    final LabelMap labelMap = new LabelMap(node.instructions);
                    if (node.instructions.size() > 0) {
                        visitor.visitInsns(0, DiffUtils.diff(
                            Collections.emptyList(),
                            new InsnListAdapter(node.instructions),
                            Equalizers.insnEqualizer(LabelMap.EMPTY, labelMap)
                        ), () -> labelMap);
                    }
                    if (!Util.isNullOrEmpty(node.localVariables)) {
                        visitor.visitLocalVariables(node.localVariables, labelMap);
                    }
                    if (!node.tryCatchBlocks.isEmpty()) {
                        visitor.visitTryCatchBlocks(node.tryCatchBlocks, labelMap);
                    }
                    if (!Util.isNullOrEmpty(node.invisibleLocalVariableAnnotations)) {
                        visitor.visitLocalVariableAnnotations(node.invisibleLocalVariableAnnotations, false, labelMap);
                    }
                    if (!Util.isNullOrEmpty(node.visibleLocalVariableAnnotations)) {
                        visitor.visitLocalVariableAnnotations(node.visibleLocalVariableAnnotations, true, labelMap);
                    }
                    visitInsnAnnotations(node.instructions, visitor, false);
                    visitInsnAnnotations(node.instructions, visitor, true);
                    visitor.visitEnd();
                }
            }
        }
    }

    private void diffMethods(
        MethodNode original,
        MethodNode modified,
        MethodDiffVisitor output
    ) {
        diffAnnotated(
            output,
            original.visibleAnnotations, modified.visibleAnnotations,
            original.invisibleAnnotations, modified.invisibleAnnotations,
            original.visibleTypeAnnotations, modified.visibleTypeAnnotations,
            original.invisibleTypeAnnotations, modified.invisibleTypeAnnotations
        );

        if (!Equalizers.annotationValue(original.annotationDefault, modified.annotationDefault)) {
            output.visitAnnotationDefault(modified.annotationDefault);
        }

        if (
            original.visibleAnnotableParameterCount != modified.visibleAnnotableParameterCount ||
            !Equalizers.arrayEquals(
                original.visibleParameterAnnotations, modified.visibleParameterAnnotations,
                (a, b) -> Equalizers.listEquals(a, b, Equalizers::annotation)
            )
        ) {
            final int paramCount = Type.getArgumentTypes(modified.desc).length;
            final List<Patch<AnnotationNode>> patches = new ArrayList<>(paramCount);
            for (int i = 0; i < paramCount; i++) {
                patches.add(DiffUtils.diff(
                    Util.getListFromArray(original.visibleParameterAnnotations, i),
                    Util.getListFromArray(modified.visibleParameterAnnotations, i),
                    Equalizers::annotation
                ));
            }
            output.visitParameterAnnotations(modified.visibleAnnotableParameterCount, patches, true);
        }

        if (
            original.invisibleAnnotableParameterCount != modified.invisibleAnnotableParameterCount ||
                !Equalizers.arrayEquals(
                    original.invisibleParameterAnnotations, modified.invisibleParameterAnnotations,
                    (a, b) -> Equalizers.listEquals(a, b, Equalizers::annotation)
                )
        ) {
            final int paramCount = Type.getArgumentTypes(modified.desc).length;
            final List<Patch<AnnotationNode>> patches = new ArrayList<>(paramCount);
            for (int i = 0; i < paramCount; i++) {
                patches.add(DiffUtils.diff(
                    Util.getListFromArray(original.invisibleParameterAnnotations, i),
                    Util.getListFromArray(modified.invisibleParameterAnnotations, i),
                    Equalizers::annotation
                ));
            }
            output.visitParameterAnnotations(modified.invisibleAnnotableParameterCount, patches, false);
        }

        if (!Equalizers.listEquals(original.parameters, modified.parameters, Equalizers::parameter)) {
            output.visitParameters(DiffUtils.diff(
                Util.nullToEmpty(original.parameters),
                Util.nullToEmpty(modified.parameters),
                Equalizers::parameter
            ));
        }

        diffAttributable(output, original.attrs, modified.attrs);

        if (modified.maxStack != original.maxStack || modified.maxLocals != original.maxLocals) {
            output.visitMaxs(modified.maxStack, modified.maxLocals);
        }

        final LabelMap originalMap = new LabelMap(original.instructions);
        final LabelMap modifiedMap = new LabelMap(modified.instructions);

        final boolean insnsEquals = Equalizers.insnList(original.instructions, modified.instructions, originalMap, modifiedMap);
        if (!insnsEquals) {
            output.visitInsns(original.instructions.size(), DiffUtils.diff(
                new InsnListAdapter(original.instructions),
                new InsnListAdapter(modified.instructions),
                Equalizers.insnEqualizer(originalMap, modifiedMap)
            ), () -> modifiedMap);
        }

        if (!Equalizers.listEquals(
            original.localVariables, modified.localVariables,
            Equalizers.localVariableEqualizer(originalMap, modifiedMap)
        ) || (!insnsEquals && (!Util.isNullOrEmpty(original.localVariables) || !Util.isNullOrEmpty(modified.localVariables)))) {
            output.visitLocalVariables(Util.nullToEmpty(modified.localVariables), modifiedMap);
        }

        if (!Equalizers.listEquals(
            original.tryCatchBlocks, modified.tryCatchBlocks,
            Equalizers.tryCatchBlockEqualizer(originalMap, modifiedMap)
        ) || (!insnsEquals && (!original.tryCatchBlocks.isEmpty() || !modified.tryCatchBlocks.isEmpty()))) {
            output.visitTryCatchBlocks(modified.tryCatchBlocks, modifiedMap);
        }

        if (
            !Equalizers.listEquals(
                original.invisibleLocalVariableAnnotations, modified.invisibleLocalVariableAnnotations,
                Equalizers.localVariableAnnotationEqualizer(originalMap, modifiedMap)
            ) || (!insnsEquals && (
                !Util.isNullOrEmpty(original.invisibleLocalVariableAnnotations) ||
                !Util.isNullOrEmpty(modified.invisibleLocalVariableAnnotations)
            ))
        ) {
            output.visitLocalVariableAnnotations(
                Util.nullToEmpty(modified.invisibleLocalVariableAnnotations), false, modifiedMap
            );
        }

        if (
            !Equalizers.listEquals(
                original.visibleLocalVariableAnnotations, modified.visibleLocalVariableAnnotations,
                Equalizers.localVariableAnnotationEqualizer(originalMap, modifiedMap)
            ) || (!insnsEquals && (
                !Util.isNullOrEmpty(original.visibleLocalVariableAnnotations) ||
                    !Util.isNullOrEmpty(modified.visibleLocalVariableAnnotations)
            ))
        ) {
            output.visitLocalVariableAnnotations(
                Util.nullToEmpty(modified.visibleLocalVariableAnnotations), true, modifiedMap
            );
        }

        visitInsnAnnotations(modified.instructions, output, false);
        visitInsnAnnotations(modified.instructions, output, true);

        output.visitEnd();
    }

    private void visitInsnAnnotations(InsnList insnList, MethodDiffVisitor visitor, boolean visible) {
        List<Integer> indices = null;
        List<TypeAnnotationNode> annotations = null;
        int i = -1;
        for (final AbstractInsnNode insn : insnList) {
            i++;
            final List<TypeAnnotationNode> insnAnnotations = visible ? insn.visibleTypeAnnotations : insn.invisibleTypeAnnotations;
            if (insnAnnotations == null) continue;
            if (indices == null) {
                indices = new ArrayList<>();
                annotations = new ArrayList<>();
            }
            for (final TypeAnnotationNode annotation : insnAnnotations) {
                indices.add(i);
                annotations.add(annotation);
            }
        }
        if (indices != null) {
            visitor.visitInsnAnnotations(indices.stream().mapToInt(Integer::intValue).toArray(), annotations, visible);
        }
    }

    private void diffAnnotated(
        AnnotatedElementVisitor output,
        List<AnnotationNode> originalVisibleAnnotations, List<AnnotationNode> modifiedVisibleAnnotations,
        List<AnnotationNode> originalInvisibleAnnotations, List<AnnotationNode> modifiedInvisibleAnnotations,
        List<TypeAnnotationNode> originalVisibleTypeAnnotations, List<TypeAnnotationNode> modifiedVisibleTypeAnnotations,
        List<TypeAnnotationNode> originalInvisibleTypeAnnotations, List<TypeAnnotationNode> modifiedInvisibleTypeAnnotations
    ) {
        if (!Equalizers.listEquals(originalVisibleAnnotations, modifiedVisibleAnnotations, Equalizers::annotation)) {
            output.visitAnnotations(DiffUtils.diff(
                Util.nullToEmpty(originalVisibleAnnotations),
                Util.nullToEmpty(modifiedVisibleAnnotations),
                Equalizers::annotation
            ), true);
        }

        if (!Equalizers.listEquals(originalInvisibleAnnotations, modifiedInvisibleAnnotations, Equalizers::annotation)) {
            output.visitAnnotations(DiffUtils.diff(
                Util.nullToEmpty(originalInvisibleAnnotations),
                Util.nullToEmpty(modifiedInvisibleAnnotations),
                Equalizers::annotation
            ), false);
        }

        if (!Equalizers.listEquals(originalVisibleTypeAnnotations, modifiedVisibleTypeAnnotations, Equalizers::typeAnnotation)) {
            output.visitTypeAnnotations(DiffUtils.diff(
                Util.nullToEmpty(originalVisibleTypeAnnotations),
                Util.nullToEmpty(modifiedVisibleTypeAnnotations),
                Equalizers::typeAnnotation
            ), true);
        }

        if (!Equalizers.listEquals(originalInvisibleTypeAnnotations, modifiedInvisibleTypeAnnotations, Equalizers::typeAnnotation)) {
            output.visitTypeAnnotations(DiffUtils.diff(
                Util.nullToEmpty(originalInvisibleTypeAnnotations),
                Util.nullToEmpty(modifiedInvisibleTypeAnnotations),
                Equalizers::typeAnnotation
            ), false);
        }
    }

    private void diffAttributable(
        CustomAttributableVisitor output,
        List<Attribute> originalAttrs, List<Attribute> modifiedAttrs
    ) {
        final Map<String, Attribute> bAttributes = new LinkedHashMap<>();
        if (modifiedAttrs != null) {
            for (final Attribute attr : modifiedAttrs) {
                bAttributes.put(attr.type, attr);
            }
        }
        if (originalAttrs != null) {
            for (final Attribute attr : originalAttrs) {
                if (!bAttributes.containsKey(attr.type)) {
                    output.visitCustomAttribute(attr.type, null);
                    continue;
                }
                final byte[] aContents = ReflectUtils.getAttributeContent(attr);
                final byte[] bContents = ReflectUtils.getAttributeContent(bAttributes.remove(attr.type));
                if (!Arrays.equals(aContents, bContents)) {
                    try {
                        output.visitCustomAttribute(attr.type, delta.compute(aContents, bContents));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
        for (final Attribute attr : bAttributes.values()) {
            output.visitCustomAttribute(attr.type, ReflectUtils.getAttributeContent(attr));
        }
    }
}

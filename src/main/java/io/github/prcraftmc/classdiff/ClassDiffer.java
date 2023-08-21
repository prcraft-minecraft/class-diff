package io.github.prcraftmc.classdiff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.nothome.delta.Delta;
import io.github.prcraftmc.classdiff.format.*;
import io.github.prcraftmc.classdiff.util.Equalizers;
import io.github.prcraftmc.classdiff.util.MemberName;
import io.github.prcraftmc.classdiff.util.ReflectUtils;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
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
                original.interfaces != null ? original.interfaces : Collections.emptyList(),
                modified.interfaces != null ? modified.interfaces : Collections.emptyList()
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
                original.innerClasses != null ? original.innerClasses : Collections.emptyList(),
                modified.innerClasses != null ? modified.innerClasses : Collections.emptyList(),
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
                original.nestMembers != null ? original.nestMembers : Collections.emptyList(),
                modified.nestMembers != null ? modified.nestMembers : Collections.emptyList()
            ));
        }

        if (!Objects.equals(original.permittedSubclasses, modified.permittedSubclasses)) {
            output.visitPermittedSubclasses(DiffUtils.diff(
                original.permittedSubclasses != null ? original.permittedSubclasses : Collections.emptyList(),
                modified.permittedSubclasses != null ? modified.permittedSubclasses : Collections.emptyList()
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
                    aModule.packages != null ? aModule.packages : Collections.emptyList(),
                    bModule.packages != null ? bModule.packages : Collections.emptyList()
                ));
            }

            if (!Equalizers.listEquals(aModule.requires, bModule.requires, Equalizers::moduleRequire)) {
                moduleOut.visitRequires(DiffUtils.diff(
                    aModule.requires != null ? aModule.requires : Collections.emptyList(),
                    bModule.requires != null ? bModule.requires : Collections.emptyList(),
                    Equalizers::moduleRequire
                ));
            }

            if (!Equalizers.listEquals(aModule.exports, bModule.exports, Equalizers::moduleExport)) {
                moduleOut.visitExports(DiffUtils.diff(
                    aModule.exports != null ? aModule.exports : Collections.emptyList(),
                    bModule.exports != null ? bModule.exports : Collections.emptyList(),
                    Equalizers::moduleExport
                ));
            }

            if (!Equalizers.listEquals(aModule.opens, bModule.opens, Equalizers::moduleOpen)) {
                moduleOut.visitOpens(DiffUtils.diff(
                    aModule.opens != null ? aModule.opens : Collections.emptyList(),
                    bModule.opens != null ? bModule.opens : Collections.emptyList(),
                    Equalizers::moduleOpen
                ));
            }

            if (!Objects.equals(aModule.packages, bModule.packages)) {
                moduleOut.visitUses(DiffUtils.diff(
                    aModule.uses != null ? aModule.uses : Collections.emptyList(),
                    bModule.uses != null ? bModule.uses : Collections.emptyList()
                ));
            }

            if (!Equalizers.listEquals(aModule.provides, bModule.provides, Equalizers::moduleProvide)) {
                moduleOut.visitProvides(DiffUtils.diff(
                    aModule.provides != null ? aModule.provides : Collections.emptyList(),
                    bModule.provides != null ? bModule.provides : Collections.emptyList(),
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

    private void diffAnnotated(
        AnnotatedElementVisitor output,
        List<AnnotationNode> originalVisibleAnnotations, List<AnnotationNode> modifiedVisibleAnnotations,
        List<AnnotationNode> originalInvisibleAnnotations, List<AnnotationNode> modifiedInvisibleAnnotations,
        List<TypeAnnotationNode> originalVisibleTypeAnnotations, List<TypeAnnotationNode> modifiedVisibleTypeAnnotations,
        List<TypeAnnotationNode> originalInvisibleTypeAnnotations, List<TypeAnnotationNode> modifiedInvisibleTypeAnnotations
    ) {
        if (!Equalizers.listEquals(originalVisibleAnnotations, modifiedVisibleAnnotations, Equalizers::annotation)) {
            output.visitAnnotations(DiffUtils.diff(
                originalVisibleAnnotations != null ? originalVisibleAnnotations : Collections.emptyList(),
                modifiedVisibleAnnotations != null ? modifiedVisibleAnnotations : Collections.emptyList(),
                Equalizers::annotation
            ), true);
        }

        if (!Equalizers.listEquals(originalInvisibleAnnotations, modifiedInvisibleAnnotations, Equalizers::annotation)) {
            output.visitAnnotations(DiffUtils.diff(
                originalInvisibleAnnotations != null ? originalInvisibleAnnotations : Collections.emptyList(),
                modifiedInvisibleAnnotations != null ? modifiedInvisibleAnnotations : Collections.emptyList(),
                Equalizers::annotation
            ), false);
        }

        if (!Equalizers.listEquals(originalVisibleTypeAnnotations, modifiedVisibleTypeAnnotations, Equalizers::typeAnnotation)) {
            output.visitTypeAnnotations(DiffUtils.diff(
                originalVisibleTypeAnnotations != null ? originalVisibleTypeAnnotations : Collections.emptyList(),
                modifiedVisibleTypeAnnotations != null ? modifiedVisibleTypeAnnotations : Collections.emptyList(),
                Equalizers::typeAnnotation
            ), true);
        }

        if (!Equalizers.listEquals(originalInvisibleTypeAnnotations, modifiedInvisibleTypeAnnotations, Equalizers::typeAnnotation)) {
            output.visitTypeAnnotations(DiffUtils.diff(
                originalInvisibleTypeAnnotations != null ? originalInvisibleTypeAnnotations : Collections.emptyList(),
                modifiedInvisibleTypeAnnotations != null ? modifiedInvisibleTypeAnnotations : Collections.emptyList(),
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

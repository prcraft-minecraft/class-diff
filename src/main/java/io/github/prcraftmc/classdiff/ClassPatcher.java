package io.github.prcraftmc.classdiff;

import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.nothome.delta.GDiffPatcher;
import io.github.prcraftmc.classdiff.format.DiffReader;
import io.github.prcraftmc.classdiff.format.DiffVisitor;
import io.github.prcraftmc.classdiff.format.RecordComponentDiffVisitor;
import io.github.prcraftmc.classdiff.util.MemberName;
import io.github.prcraftmc.classdiff.util.ReflectUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

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
            return null;
        }

        RecordComponentNode recordNode = null;
        for (final RecordComponentNode test : node.recordComponents) {
            if (test.name.equals(name) && test.descriptor.equals(descriptor)) {
                recordNode = test;
                break;
            }
        }
        if (recordNode == null) {
            return null;
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
}

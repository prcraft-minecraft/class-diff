package io.github.prcraftmc.classdiff;

import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.nothome.delta.GDiffPatcher;
import io.github.prcraftmc.classdiff.format.DiffReader;
import io.github.prcraftmc.classdiff.format.DiffVisitor;
import io.github.prcraftmc.classdiff.util.ReflectUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;

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

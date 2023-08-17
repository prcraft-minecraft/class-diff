package io.github.prcraftmc.classdiff;

import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.nothome.delta.GDiffPatcher;
import io.github.prcraftmc.classdiff.format.DiffReader;
import io.github.prcraftmc.classdiff.format.DiffVisitor;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

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

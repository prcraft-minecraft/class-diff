package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InnerClassNode;

public class DiffVisitor {
    @Nullable
    private final DiffVisitor delegate;

    public DiffVisitor() {
        delegate = null;
    }

    protected DiffVisitor(@Nullable DiffVisitor delegate) {
        this.delegate = delegate;
    }

    @Nullable
    public DiffVisitor getDelegate() {
        return delegate;
    }

    public void visit(
        int diffVersion,
        int classVersion,
        int access,
        @Nullable String name,
        @Nullable String signature,
        @Nullable String superName,
        @Nullable Patch<String> interfaces
    ) {
        if (delegate != null) {
            delegate.visit(diffVersion, classVersion, access, name, signature, superName, interfaces);
        }
    }

    public void visitSource(@Nullable String source, @Nullable String debug) {
        if (delegate != null) {
            delegate.visitSource(source, debug);
        }
    }

    public void visitInnerClasses(Patch<InnerClassNode> patch) {
        if (delegate != null) {
            delegate.visitInnerClasses(patch);
        }
    }

    public void visitOuterClass(
        @Nullable String className,
        @Nullable String methodName,
        @Nullable String methodDescriptor
    ) {
        if (delegate != null) {
            delegate.visitOuterClass(className, methodName, methodDescriptor);
        }
    }

    public void visitNestHost(@Nullable String nestHost) {
        if (delegate != null) {
            delegate.visitNestHost(nestHost);
        }
    }

    public void visitNestMembers(Patch<String> patch) {
        if (delegate != null) {
            delegate.visitNestMembers(patch);
        }
    }

    public void visitPermittedSubclasses(Patch<String> patch) {
        if (delegate != null) {
            delegate.visitPermittedSubclasses(patch);
        }
    }

    public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
        if (delegate != null) {
            delegate.visitAnnotations(patch, visible);
        }
    }

    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        if (delegate != null) {
            delegate.visitCustomAttribute(name, patchOrContents);
        }
    }
}

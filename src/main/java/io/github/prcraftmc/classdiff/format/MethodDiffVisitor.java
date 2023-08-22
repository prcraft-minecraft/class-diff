package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import java.util.List;

public abstract class MethodDiffVisitor implements AnnotatedElementVisitor, CustomAttributableVisitor {
    @Nullable
    private final MethodDiffVisitor delegate;

    public MethodDiffVisitor() {
        delegate = null;
    }

    protected MethodDiffVisitor(@Nullable MethodDiffVisitor delegate) {
        this.delegate = delegate;
    }

    @Nullable
    public MethodDiffVisitor getDelegate() {
        return delegate;
    }

    @Override
    public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
        if (delegate != null) {
            delegate.visitAnnotations(patch, visible);
        }
    }

    @Override
    public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
        if (delegate != null) {
            delegate.visitTypeAnnotations(patch, visible);
        }
    }

    public void visitAnnotationDefault(@Nullable Object value) {
        if (delegate != null) {
            delegate.visitAnnotationDefault(value);
        }
    }

    public void visitParameterAnnotations(int annotableCount, List<Patch<AnnotationNode>> patches, boolean visible) {
        if (delegate != null) {
            delegate.visitParameterAnnotations(annotableCount, patches, visible);
        }
    }

    @Override
    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        if (delegate != null) {
            delegate.visitCustomAttribute(name, patchOrContents);
        }
    }

    public void visitEnd() {
        if (delegate != null) {
            delegate.visitEnd();
        }
    }
}

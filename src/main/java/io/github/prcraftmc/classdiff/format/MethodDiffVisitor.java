package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.util.LabelMap;
import io.github.prcraftmc.classdiff.util.SyntheticLabelNode;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.function.Supplier;

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

    public void visitParameters(Patch<ParameterNode> parameters) {
        if (delegate != null) {
            delegate.visitParameters(parameters);
        }
    }

    @Override
    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        if (delegate != null) {
            delegate.visitCustomAttribute(name, patchOrContents);
        }
    }

    public void visitMaxs(int maxStack, int maxLocals) {
        if (delegate != null) {
            delegate.visitMaxs(maxStack, maxLocals);
        }
    }

    /**
     * @apiNote These insns may not have annotations and may use {@link SyntheticLabelNode}s.
     */
    public void visitInsns(Patch<AbstractInsnNode> patch, Supplier<LabelMap> patchedLabelMap) {
        if (delegate != null) {
            delegate.visitInsns(patch, patchedLabelMap);
        }
    }

    public void visitLocalVariables(List<LocalVariableNode> newLocals, @Nullable LabelMap useMap) {
        if (delegate != null) {
            delegate.visitLocalVariables(newLocals, useMap);
        }
    }

    public void visitTryCatchBlocks(List<TryCatchBlockNode> newBlocks, @Nullable LabelMap useMap) {
        if (delegate != null) {
            delegate.visitTryCatchBlocks(newBlocks, useMap);
        }
    }

    public void visitLocalVariableAnnotations(
        List<LocalVariableAnnotationNode> annotations,
        boolean visible,
        @Nullable LabelMap useMap
    ) {
        if (delegate != null) {
            delegate.visitLocalVariableAnnotations(annotations, visible, useMap);
        }
    }

    public void visitInsnAnnotations(int[] indices, List<TypeAnnotationNode> annotations, boolean visible) {
        if (delegate != null) {
            delegate.visitInsnAnnotations(indices, annotations, visible);
        }
    }

    public void visitEnd() {
        if (delegate != null) {
            delegate.visitEnd();
        }
    }
}

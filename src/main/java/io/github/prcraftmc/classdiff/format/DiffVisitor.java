package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.util.MemberName;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

public class DiffVisitor implements AnnotatedElementVisitor, CustomAttributableVisitor {
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

    public void visitRecordComponents(Patch<MemberName> patch) {
        if (delegate != null) {
            delegate.visitRecordComponents(patch);
        }
    }

    @Nullable
    public RecordComponentDiffVisitor visitRecordComponent(
        String name,
        String descriptor,
        @Nullable String signature
    ) {
        if (delegate != null) {
            return delegate.visitRecordComponent(name, descriptor, signature);
        }
        return null;
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

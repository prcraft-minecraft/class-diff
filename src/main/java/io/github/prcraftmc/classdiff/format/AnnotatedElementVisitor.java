package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

public interface AnnotatedElementVisitor {
    void visitAnnotations(Patch<AnnotationNode> patch, boolean visible);

    void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible);
}

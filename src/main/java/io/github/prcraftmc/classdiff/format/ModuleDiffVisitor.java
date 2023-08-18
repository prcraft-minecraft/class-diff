package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ModuleExportNode;
import org.objectweb.asm.tree.ModuleOpenNode;
import org.objectweb.asm.tree.ModuleProvideNode;
import org.objectweb.asm.tree.ModuleRequireNode;

public class ModuleDiffVisitor {
    @Nullable
    private final ModuleDiffVisitor delegate;

    public ModuleDiffVisitor() {
        delegate = null;
    }

    protected ModuleDiffVisitor(@Nullable ModuleDiffVisitor delegate) {
        this.delegate = delegate;
    }

    @Nullable
    public ModuleDiffVisitor getDelegate() {
        return delegate;
    }

    public void visitMainClass(@Nullable String mainClass) {
        if (delegate != null) {
            delegate.visitMainClass(mainClass);
        }
    }

    public void visitPackages(Patch<String> patch) {
        if (delegate != null) {
            delegate.visitPackages(patch);
        }
    }

    public void visitRequires(Patch<ModuleRequireNode> patch) {
        if (delegate != null) {
            delegate.visitRequires(patch);
        }
    }

    public void visitExports(Patch<ModuleExportNode> patch) {
        if (delegate != null) {
            delegate.visitExports(patch);
        }
    }

    public void visitOpens(Patch<ModuleOpenNode> patch) {
        if (delegate != null) {
            delegate.visitOpens(patch);
        }
    }

    public void visitUses(Patch<String> patch) {
        if (delegate != null) {
            delegate.visitUses(patch);
        }
    }

    public void visitProvides(Patch<ModuleProvideNode> patch) {
        if (delegate != null) {
            delegate.visitProvides(patch);
        }
    }

    public void visitEnd() {
        if (delegate != null) {
            delegate.visitEnd();
        }
    }
}

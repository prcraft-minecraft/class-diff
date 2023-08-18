package io.github.prcraftmc.classdiff.format;

import org.jetbrains.annotations.Nullable;

public interface CustomAttributableVisitor {
    void visitCustomAttribute(String name, byte @Nullable [] patchOrContents);
}

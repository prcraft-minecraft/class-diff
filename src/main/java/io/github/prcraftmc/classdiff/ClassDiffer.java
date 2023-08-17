package io.github.prcraftmc.classdiff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.nothome.delta.Delta;
import io.github.prcraftmc.classdiff.format.DiffConstants;
import io.github.prcraftmc.classdiff.format.DiffVisitor;
import io.github.prcraftmc.classdiff.util.Equalizers;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

public class ClassDiffer {
    private final Delta delta = new Delta();
    private final DiffVisitor output;

    public ClassDiffer(DiffVisitor output) {
        this.output = output;
    }

    public static void diff(ClassNode original, ClassNode modified, DiffVisitor result) {
        new ClassDiffer(result).accept(original, modified);
    }

    public static void diff(ClassReader original, ClassReader modified, DiffVisitor result) {
        final ClassNode aNode = new ClassNode();
        original.accept(aNode, 0);
        final ClassNode bNode = new ClassNode();
        modified.accept(bNode, 0);
        diff(aNode, bNode, result);
    }

    public void accept(ClassNode original, ClassNode modified) {
        final Patch<String> interfacePatch;
        if (Objects.equals(original.interfaces, modified.interfaces)) {
            interfacePatch = null;
        } else {
            interfacePatch = DiffUtils.diff(
                original.interfaces != null ? original.interfaces : Collections.emptyList(),
                modified.interfaces != null ? modified.interfaces : Collections.emptyList()
            );
        }

        output.visit(
            DiffConstants.V1,
            modified.version == original.version ? -1 : modified.version,
            modified.access == original.access ? -1 : modified.access,
            modified.name.equals(original.name) ? null : modified.name,
            Objects.equals(modified.signature, original.signature) ? null : (modified.signature != null ? modified.signature : ""),
            Objects.equals(modified.superName, original.superName) ? null : (modified.superName != null ? modified.superName : ""),
            interfacePatch
        );

        if (
            !Objects.equals(original.sourceFile, modified.sourceFile) ||
                !Objects.equals(original.sourceDebug, modified.sourceDebug)
        ) {
            output.visitSource(
                Objects.equals(original.sourceFile, modified.sourceFile) ? null : modified.sourceFile,
                Objects.equals(original.sourceDebug, modified.sourceDebug) ? null : modified.sourceDebug
            );
        }

        if (!Equalizers.listEquals(original.innerClasses, modified.innerClasses, Equalizers.INNER_CLASS_NODE)) {
            output.visitInnerClasses(DiffUtils.diff(
                original.innerClasses != null ? original.innerClasses : Collections.emptyList(),
                modified.innerClasses != null ? modified.innerClasses : Collections.emptyList(),
                Equalizers.INNER_CLASS_NODE
            ));
        }

        if (
            !Objects.equals(original.outerClass, modified.outerClass) ||
                !Objects.equals(original.outerMethod, modified.outerMethod) ||
                !Objects.equals(original.outerMethodDesc, modified.outerMethodDesc)
        ) {
            output.visitOuterClass(
                Objects.equals(original.outerClass, modified.outerClass)
                    ? null : modified.outerClass,
                Objects.equals(original.outerMethod, modified.outerMethod)
                    ? null : (modified.outerMethod == null ? "" : modified.outerMethod),
                Objects.equals(original.outerMethodDesc, modified.outerMethodDesc)
                    ? null : (modified.outerMethodDesc == null ? "" : modified.outerMethodDesc)
            );
        }

        {
            final Map<String, Attribute> bAttributes = new LinkedHashMap<>();
            if (modified.attrs != null) {
                for (final Attribute attr : modified.attrs) {
                    bAttributes.put(attr.type, attr);
                }
            }
            if (original.attrs != null) {
                for (final Attribute attr : original.attrs) {
                    if (!bAttributes.containsKey(attr.type)) {
                        output.visitCustomAttribute(attr.type, null);
                        continue;
                    }
                    final byte[] aContents = ReflectUtils.getAttributeContent(attr);
                    final byte[] bContents = ReflectUtils.getAttributeContent(bAttributes.remove(attr.type));
                    if (!Arrays.equals(aContents, bContents)) {
                        try {
                            output.visitCustomAttribute(attr.type, delta.compute(aContents, bContents));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                }
            }
            for (final Attribute attr : bAttributes.values()) {
                output.visitCustomAttribute(attr.type, ReflectUtils.getAttributeContent(attr));
            }
        }
    }
}

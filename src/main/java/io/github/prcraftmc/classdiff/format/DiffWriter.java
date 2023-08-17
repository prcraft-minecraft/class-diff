package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.util.PatchWriter;
import io.github.prcraftmc.classdiff.util.ReflectUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiffWriter extends DiffVisitor {
    private final SymbolTable symbolTable = new SymbolTable();
    private final PatchWriter<String> classPatchWriter = new PatchWriter<>((vec, value) ->
        vec.putShort(symbolTable.addConstantClass(value).index)
    );
    private final PatchWriter<AnnotationNode> annotationPatchWriter = new PatchWriter<>((vec, value) -> {
        vec.putShort(symbolTable.addConstantUtf8(value.desc)).putShort(0);
        value.accept(new AnnotationWriter(symbolTable, true, vec));
    });
    private final PatchWriter<TypeAnnotationNode> typeAnnotationPatchWriter = new PatchWriter<>((vec, value) -> {
        ReflectUtils.invokeTypeReferencePutTarget(value.typeRef, vec);
        ReflectUtils.invokeTypePathPut(value.typePath, vec);
        vec.putShort(symbolTable.addConstantUtf8(value.desc)).putShort(0);
        value.accept(new AnnotationWriter(symbolTable, true, vec));
    });

    private int diffVersion;
    private int classVersion;
    private int access;
    private int name;
    private int signature;
    private int superName;
    private ByteVector interfaces;

    private int source;
    private int debug;

    private ByteVector innerClasses;

    private int outerClass;
    private int outerMethod;
    private int outerMethodDesc;

    private int nestHost;

    private ByteVector nestMembers;

    private ByteVector permittedSubclasses;

    private ByteVector visibleAnnotations;
    private ByteVector invisibleAnnotations;

    private ByteVector visibleTypeAnnotations;
    private ByteVector invisibleTypeAnnotations;

    private final Map<Integer, byte @Nullable []> attributes = new LinkedHashMap<>();

    public DiffWriter() {
    }

    public DiffWriter(@Nullable DiffVisitor delegate) {
        super(delegate);
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
        super.visit(diffVersion, classVersion, access, name, signature, superName, interfaces);

        this.diffVersion = diffVersion;
        this.classVersion = classVersion;
        this.access = access;
        this.name = name != null ? symbolTable.addConstantClass(name).index : 0;
        this.signature = signature != null ? symbolTable.addConstantUtf8(signature) : 0;
        this.superName = superName != null ? symbolTable.addConstantClass(superName).index : 0;

        if (interfaces != null) {
            classPatchWriter.write(this.interfaces = new ByteVector(), interfaces);
        } else {
            this.interfaces = null;
        }
    }

    @Override
    public void visitSource(@Nullable String source, @Nullable String debug) {
        super.visitSource(source, debug);

        this.source = source != null ? symbolTable.addConstantUtf8(source) : 0;
        this.debug = debug != null ? symbolTable.addConstantUtf8(debug) : 0;
    }

    @Override
    public void visitInnerClasses(Patch<InnerClassNode> patch) {
        super.visitInnerClasses(patch);

        new PatchWriter<InnerClassNode>((vec, value) ->
            vec.putShort(symbolTable.addConstantClass(value.name).index)
                .putShort(symbolTable.addConstantClass(value.outerName).index)
                .putShort(symbolTable.addConstantUtf8(value.innerName))
                .putShort(value.access)
        ).write(innerClasses = new ByteVector(), patch);
    }

    @Override
    public void visitOuterClass(
        @Nullable String className,
        @Nullable String methodName,
        @Nullable String methodDescriptor
    ) {
        super.visitOuterClass(className, methodName, methodDescriptor);

        outerClass = className != null ? symbolTable.addConstantClass(className).index : 0;
        outerMethod = methodName != null ? symbolTable.addConstantUtf8(methodName) : 0;
        outerMethodDesc = methodDescriptor != null ? symbolTable.addConstantUtf8(methodDescriptor) : 0;
    }

    @Override
    public void visitNestHost(@Nullable String nestHost) {
        super.visitNestHost(nestHost);

        this.nestHost = nestHost != null ? symbolTable.addConstantClass(nestHost).index : 0;
    }

    @Override
    public void visitNestMembers(Patch<String> patch) {
        super.visitNestMembers(patch);

        classPatchWriter.write(nestMembers = new ByteVector(), patch);
    }

    @Override
    public void visitPermittedSubclasses(Patch<String> patch) {
        super.visitPermittedSubclasses(patch);

        classPatchWriter.write(permittedSubclasses = new ByteVector(), patch);
    }

    @Override
    public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
        super.visitAnnotations(patch, visible);

        final ByteVector vector = new ByteVector();
        if (visible) {
            visibleAnnotations = vector;
        } else {
            invisibleAnnotations = vector;
        }
        annotationPatchWriter.write(vector, patch);
    }

    @Override
    public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
        super.visitTypeAnnotations(patch, visible);

        final ByteVector vector = new ByteVector();
        if (visible) {
            visibleTypeAnnotations = vector;
        } else {
            invisibleTypeAnnotations = vector;
        }
        typeAnnotationPatchWriter.write(vector, patch);
    }

    @Override
    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        super.visitCustomAttribute(name, patchOrContents);

        attributes.put(symbolTable.addConstantUtf8("Custom" + name), patchOrContents);
    }

    public byte[] toByteArray() {
        final ByteVector result = new ByteVector();

        result.putInt(DiffConstants.MAGIC);
        result.putShort(diffVersion);

        int attributeCount = attributes.size();
        if (source != 0 || debug != 0) {
            symbolTable.addConstantUtf8("Source");
            attributeCount++;
        }
        if (innerClasses != null) {
            symbolTable.addConstantUtf8("InnerClasses");
            attributeCount++;
        }
        if (outerClass != 0 || outerMethod != 0 || outerMethodDesc != 0) {
            symbolTable.addConstantUtf8("OuterClass");
            attributeCount++;
        }
        if (nestHost != 0) {
            symbolTable.addConstantUtf8("NestHost");
            attributeCount++;
        }
        if (nestMembers != null) {
            symbolTable.addConstantUtf8("NestMembers");
            attributeCount++;
        }
        if (permittedSubclasses != null) {
            symbolTable.addConstantUtf8("PermittedSubclasses");
            attributeCount++;
        }
        if (visibleAnnotations != null) {
            symbolTable.addConstantUtf8("VisibleAnnotations");
            attributeCount++;
        }
        if (invisibleAnnotations != null) {
            symbolTable.addConstantUtf8("InvisibleAnnotations");
            attributeCount++;
        }
        if (visibleTypeAnnotations != null) {
            symbolTable.addConstantUtf8("VisibleTypeAnnotations");
            attributeCount++;
        }
        if (invisibleTypeAnnotations != null) {
            symbolTable.addConstantUtf8("InvisibleTypeAnnotations");
            attributeCount++;
        }

        symbolTable.putConstantPool(result);

        result.putInt(classVersion);
        result.putInt(access);
        result.putShort(name);
        result.putShort(signature);
        result.putShort(superName);

        if (interfaces == null) {
            result.putShort(0);
        } else {
            result.putByteArray(ReflectUtils.getByteVectorData(interfaces), 0, interfaces.size());
        }

        result.putShort(attributeCount);
        if (source != 0 || debug != 0) {
            result.putShort(symbolTable.addConstantUtf8("Source")).putInt(4);
            result.putShort(source).putShort(debug);
        }
        if (innerClasses != null) {
            result.putShort(symbolTable.addConstantUtf8("InnerClasses")).putInt(innerClasses.size());
            result.putByteArray(ReflectUtils.getByteVectorData(innerClasses), 0, innerClasses.size());
        }
        if (outerClass != 0 || outerMethod != 0 || outerMethodDesc != 0) {
            result.putShort(symbolTable.addConstantUtf8("OuterClass")).putInt(6);
            result.putShort(outerClass).putShort(outerMethod).putShort(outerMethodDesc);
        }
        if (nestHost != 0) {
            result.putShort(symbolTable.addConstantUtf8("NestHost")).putInt(2);
            result.putShort(nestHost);
        }
        if (nestMembers != null) {
            result.putShort(symbolTable.addConstantUtf8("NestMembers")).putInt(nestMembers.size());
            result.putByteArray(ReflectUtils.getByteVectorData(nestMembers), 0, nestMembers.size());
        }
        if (permittedSubclasses != null) {
            result.putShort(symbolTable.addConstantUtf8("PermittedSubclasses")).putInt(permittedSubclasses.size());
            result.putByteArray(ReflectUtils.getByteVectorData(permittedSubclasses), 0, permittedSubclasses.size());
        }
        if (visibleAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("VisibleAnnotations")).putInt(visibleAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(visibleAnnotations), 0, visibleAnnotations.size());
        }
        if (invisibleAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("InvisibleAnnotations")).putInt(invisibleAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(invisibleAnnotations), 0, invisibleAnnotations.size());
        }
        if (visibleTypeAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("VisibleTypeAnnotations")).putInt(visibleTypeAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(visibleTypeAnnotations), 0, visibleTypeAnnotations.size());
        }
        if (invisibleTypeAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("InvisibleTypeAnnotations")).putInt(invisibleTypeAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(invisibleTypeAnnotations), 0, invisibleTypeAnnotations.size());
        }
        for (final Map.Entry<Integer, byte @Nullable []> entry : attributes.entrySet()) {
            result.putShort(entry.getKey());
            final byte @Nullable [] value = entry.getValue();
            if (value == null) {
                result.putInt(1).putByte(0);
            } else {
                result.putInt(value.length + 1).putByteArray(value, 0, value.length);
            }
        }

        return Arrays.copyOf(ReflectUtils.getByteVectorData(result), result.size());
    }
}

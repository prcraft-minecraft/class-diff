package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.ReflectUtils;
import io.github.prcraftmc.classdiff.util.PatchWriter;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.tree.InnerClassNode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiffWriter extends DiffVisitor {
    private final SymbolTable symbolTable = new SymbolTable();
    private final PatchWriter<String> classPatchWriter = new PatchWriter<>((vec, value) ->
        vec.putShort(symbolTable.addConstantClass(value).index)
    );

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

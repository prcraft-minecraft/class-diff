package io.github.prcraftmc.classdiff.format;

import io.github.prcraftmc.classdiff.ReflectUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class AnnotationWriter extends AnnotationVisitor {
    private final SymbolTable symbolTable;
    private final boolean useNamedValues;
    private final ByteVector annotation;
    private final int numElementValuePairsOffset;
    private int numElementValuePairs;

    public AnnotationWriter(SymbolTable symbolTable, boolean useNamedValues, ByteVector annotation) {
        super(Opcodes.ASM9);
        this.symbolTable = symbolTable;
        this.useNamedValues = useNamedValues;
        this.annotation = annotation;
        numElementValuePairsOffset = annotation.size() == 0 ? -1 : annotation.size() - 2;
    }

    @Override
    public void visit(String name, Object value) {
        numElementValuePairs++;
        if (useNamedValues) {
            annotation.putShort(symbolTable.addConstantUtf8(name));
        }
        if (value instanceof String) {
            put12('s', symbolTable.addConstantUtf8((String)value));
        } else if (value instanceof Byte) {
            put12('B', symbolTable.addConstantInteger((byte)value).index);
        } else if (value instanceof Boolean) {
            put12('Z', symbolTable.addConstantInteger((boolean)value ? 1 : 0).index);
        } else if (value instanceof Character) {
            put12('C', symbolTable.addConstantInteger((char)value).index);
        } else if (value instanceof Short) {
            put12('S', symbolTable.addConstantInteger((short)value).index);
        } else if (value instanceof Type) {
            put12('c', symbolTable.addConstantUtf8(((Type)value).getDescriptor()));
        } else if (value instanceof byte[]) {
            byte[] byteArray = (byte[]) value;
            put12('[', byteArray.length);
            for (byte byteValue : byteArray) {
                put12('B', symbolTable.addConstantInteger(byteValue).index);
            }
        } else if (value instanceof boolean[]) {
            boolean[] booleanArray = (boolean[]) value;
            put12('[', booleanArray.length);
            for (boolean booleanValue : booleanArray) {
                put12('Z', symbolTable.addConstantInteger(booleanValue ? 1 : 0).index);
            }
        } else if (value instanceof short[]) {
            short[] shortArray = (short[]) value;
            put12('[', shortArray.length);
            for (short shortValue : shortArray) {
                put12('S', symbolTable.addConstantInteger(shortValue).index);
            }
        } else if (value instanceof char[]) {
            char[] charArray = (char[]) value;
            put12('[', charArray.length);
            for (char charValue : charArray) {
                put12('C', symbolTable.addConstantInteger(charValue).index);
            }
        } else if (value instanceof int[]) {
            int[] intArray = (int[]) value;
            put12('[', intArray.length);
            for (int intValue : intArray) {
                put12('I', symbolTable.addConstantInteger(intValue).index);
            }
        } else if (value instanceof long[]) {
            long[] longArray = (long[]) value;
            put12('[', longArray.length);
            for (long longValue : longArray) {
                put12('J', symbolTable.addConstantLong(longValue).index);
            }
        } else if (value instanceof float[]) {
            float[] floatArray = (float[]) value;
            put12('[', floatArray.length);
            for (float floatValue : floatArray) {
                put12('F', symbolTable.addConstantFloat(floatValue).index);
            }
        } else if (value instanceof double[]) {
            double[] doubleArray = (double[]) value;
            put12('[', doubleArray.length);
            for (double doubleValue : doubleArray) {
                put12('D', symbolTable.addConstantDouble(doubleValue).index);
            }
        } else {
            final Symbol symbol = symbolTable.addConstant(value);
            put12(".s.IFJDCS".charAt(symbol.tag), symbol.index);
        }
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        numElementValuePairs++;
        if (useNamedValues) {
            annotation.putShort(symbolTable.addConstantUtf8(name));
        }
        put12('e', symbolTable.addConstantUtf8(descriptor));
        annotation.putShort(symbolTable.addConstantUtf8(value));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        numElementValuePairs++;
        if (useNamedValues) {
            annotation.putShort(symbolTable.addConstantUtf8(name));
        }
        put12('@', symbolTable.addConstantUtf8(descriptor));
        annotation.putShort(0);
        return new AnnotationWriter(symbolTable, true, annotation);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        numElementValuePairs++;
        if (useNamedValues) {
            annotation.putShort(symbolTable.addConstantUtf8(name));
        }
        put12('[', 0);
        return new AnnotationWriter(symbolTable, false, annotation);
    }

    @Override
    public void visitEnd() {
        if (numElementValuePairsOffset != -1) {
            final byte[] data = ReflectUtils.getByteVectorData(annotation);
            data[numElementValuePairsOffset] = (byte)(numElementValuePairs >>> 8);
            data[numElementValuePairsOffset + 1] = (byte)numElementValuePairs;
        }
    }

    private void put12(int byteValue, int shortValue) {
        annotation.putByte(byteValue).putShort(shortValue);
    }
}

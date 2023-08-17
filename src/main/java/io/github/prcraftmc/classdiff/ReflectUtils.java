package io.github.prcraftmc.classdiff;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ConstantDynamic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@ApiStatus.Internal
public class ReflectUtils {
    private static final Constructor<Attribute> NEW_ATTRIBUTE;
    private static final Field ATTRIBUTE_CONTENT;

    private static final Field BYTE_VECTOR_DATA;
    private static final Field BYTE_VECTOR_LENGTH;

    private static final Field CONSTANT_DYNAMIC_BOOTSTRAP_METHOD_ARGUMENTS;

    static {
        try {
            NEW_ATTRIBUTE = Attribute.class.getDeclaredConstructor(String.class);
            ATTRIBUTE_CONTENT = Attribute.class.getDeclaredField("content");
            NEW_ATTRIBUTE.setAccessible(true);
            ATTRIBUTE_CONTENT.setAccessible(true);

            BYTE_VECTOR_DATA = ByteVector.class.getDeclaredField("data");
            BYTE_VECTOR_LENGTH = ByteVector.class.getDeclaredField("length");
            BYTE_VECTOR_DATA.setAccessible(true);
            BYTE_VECTOR_LENGTH.setAccessible(true);

            CONSTANT_DYNAMIC_BOOTSTRAP_METHOD_ARGUMENTS = ConstantDynamic.class.getDeclaredField("bootstrapMethodArguments");
            CONSTANT_DYNAMIC_BOOTSTRAP_METHOD_ARGUMENTS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Attribute newAttribute(String name) {
        try {
            return NEW_ATTRIBUTE.newInstance(name);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static byte[] getAttributeContent(Attribute attribute) {
        try {
            return (byte[])ATTRIBUTE_CONTENT.get(attribute);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void setAttributeContent(Attribute attribute, byte[] content) {
        try {
            ATTRIBUTE_CONTENT.set(attribute, content);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static byte[] getByteVectorData(ByteVector vector) {
        try {
            return (byte[])BYTE_VECTOR_DATA.get(vector);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void setByteVectorLength(ByteVector vector, int length) {
        try {
            BYTE_VECTOR_LENGTH.setInt(vector, length);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Object[] getConstantDynamicBootstrapMethodArguments(ConstantDynamic constant) {
        try {
            return (Object[])CONSTANT_DYNAMIC_BOOTSTRAP_METHOD_ARGUMENTS.get(constant);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}

package io.github.prcraftmc.classdiff;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@ApiStatus.Internal
public class ReflectUtils {
    private static final Constructor<Attribute> NEW_ATTRIBUTE;
    private static final Field ATTRIBUTE_CONTENT;

    private static final Field BYTEVECTOR_DATA;

    static {
        try {
            NEW_ATTRIBUTE = Attribute.class.getDeclaredConstructor(String.class);
            ATTRIBUTE_CONTENT = Attribute.class.getDeclaredField("content");
            NEW_ATTRIBUTE.setAccessible(true);
            ATTRIBUTE_CONTENT.setAccessible(true);

            BYTEVECTOR_DATA = ByteVector.class.getDeclaredField("data");
            BYTEVECTOR_DATA.setAccessible(true);
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
            return (byte[])BYTEVECTOR_DATA.get(vector);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}

package io.github.prcraftmc.classdiff.util;

import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.PatchFailedException;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

@ApiStatus.Internal
public class ReflectUtils {
    private static final Constructor<Attribute> NEW_ATTRIBUTE;
    private static final Field ATTRIBUTE_CONTENT;

    private static final Field BYTE_VECTOR_DATA;
    private static final Field BYTE_VECTOR_LENGTH;

    private static final Field CONSTANT_DYNAMIC_BOOTSTRAP_METHOD_ARGUMENTS;

    private static final Constructor<TypePath> NEW_TYPE_PATH;
    private static final Method TYPE_PATH_PUT;

    private static final Method TYPE_REFERENCE_PUT_TARGET;

    private static final Method ABSTRACT_DELTA_APPLY_TO;

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

            NEW_TYPE_PATH = TypePath.class.getDeclaredConstructor(byte[].class, int.class);
            TYPE_PATH_PUT = TypePath.class.getDeclaredMethod("put", TypePath.class, ByteVector.class);
            NEW_TYPE_PATH.setAccessible(true);
            TYPE_PATH_PUT.setAccessible(true);

            TYPE_REFERENCE_PUT_TARGET = TypeReference.class.getDeclaredMethod("putTarget", int.class, ByteVector.class);
            TYPE_REFERENCE_PUT_TARGET.setAccessible(true);

            ABSTRACT_DELTA_APPLY_TO = AbstractDelta.class.getDeclaredMethod("applyTo", List.class);
            ABSTRACT_DELTA_APPLY_TO.setAccessible(true);
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

    public static TypePath newTypePath(byte[] typePathContainer, int typePathOffset) {
        try {
            return NEW_TYPE_PATH.newInstance(typePathContainer, typePathOffset);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void invokeTypePathPut(TypePath typePath, ByteVector output) {
        try {
            TYPE_PATH_PUT.invoke(null, typePath, output);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void invokeTypeReferencePutTarget(int targetTypeAndInfo, ByteVector output) {
        try {
            TYPE_REFERENCE_PUT_TARGET.invoke(null, targetTypeAndInfo, output);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static <T> void invokeAbstractDeltaApplyTo(AbstractDelta<T> delta, List<T> target) throws PatchFailedException {
        try {
            ABSTRACT_DELTA_APPLY_TO.invoke(delta, target);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof PatchFailedException) {
                throw (PatchFailedException)e.getCause();
            }
            throw new RuntimeException(e);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}

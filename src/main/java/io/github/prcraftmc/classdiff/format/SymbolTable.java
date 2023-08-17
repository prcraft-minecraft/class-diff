package io.github.prcraftmc.classdiff.format;

import io.github.prcraftmc.classdiff.ReflectUtils;
import org.objectweb.asm.*;

public class SymbolTable {
    private Entry[] entries = new Entry[256];
    private int entryCount = 0;
    private int constantPoolCount = 1;
    private final ByteVector constantPool = new ByteVector();

    private int bootstrapMethodCount;
    private ByteVector bootstrapMethods;

    public int getConstantPoolCount() {
        return constantPoolCount;
    }

    public int getConstantPoolLength() {
        return constantPool.size();
    }

    public Symbol addConstantClass(String value) {
        return addConstantUtf8Reference(Symbol.CONSTANT_CLASS_TAG, value);
    }

    private Symbol addConstantUtf8Reference(int tag, String value) {
        final int hashCode = hash(tag, value);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == tag && entry.hashCode == hashCode && entry.value.equals(value)) {
                return entry;
            }
            entry = entry.next;
        }
        put12(tag, addConstantUtf8(value));
        return put(new Entry(constantPoolCount++, tag, value, hashCode));
    }

    public int addConstantUtf8(String value) {
        final int hashCode = hash(Symbol.CONSTANT_UTF8_TAG, value);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == Symbol.CONSTANT_UTF8_TAG && entry.hashCode == hashCode && entry.value.equals(value)) {
                return entry.index;
            }
            entry = entry.next;
        }
        constantPool.putByte(Symbol.CONSTANT_UTF8_TAG).putUTF8(value);
        return put(new Entry(constantPoolCount++, Symbol.CONSTANT_UTF8_TAG, value, hashCode)).index;
    }

    private void put12(int byteValue, int shortValue) {
        constantPool.putByte(byteValue).putShort(shortValue);
    }

    private Entry get(int hashCode) {
        return entries[hashCode % entries.length];
    }

    private Entry put(Entry entry) {
        if (entryCount > entries.length * 3 / 4) {
            final int currentCapacity = entries.length;
            final int newCapacity = currentCapacity * 2 + 1;
            final Entry[] newEntries = new Entry[newCapacity];
            for (int i = currentCapacity - 1; i >= 0; i--) {
                Entry currentEntry = entries[i];
                while (currentEntry != null) {
                    final int newCurrentEntryIndex = currentEntry.hashCode % newCapacity;
                    final Entry nextEntry = currentEntry.next;
                    currentEntry.next = newEntries[newCurrentEntryIndex];
                    newEntries[newCurrentEntryIndex] = currentEntry;
                    currentEntry = nextEntry;
                }
            }
            entries = newEntries;
        }
        entryCount++;
        final int index = entry.hashCode % entries.length;
        entry.next = entries[index];
        return entries[index] = entry;
    }

    public void putConstantPool(ByteVector output) {
        output.putShort(constantPoolCount).putByteArray(ReflectUtils.getByteVectorData(constantPool), 0, constantPool.size());
    }

    public Symbol addConstantInteger(int value) {
        return addConstantIntegerOrFloat(Symbol.CONSTANT_INTEGER_TAG, value);
    }

    public Symbol addConstantFloat(float value) {
        return addConstantIntegerOrFloat(Symbol.CONSTANT_FLOAT_TAG, Float.floatToRawIntBits(value));
    }

    private Symbol addConstantIntegerOrFloat(int tag, int value) {
        final int hashCode = hash(tag, value);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == tag && entry.hashCode == hashCode && entry.data == value) {
                return entry;
            }
            entry = entry.next;
        }
        constantPool.putByte(tag).putInt(value);
        return put(new Entry(constantPoolCount++, tag, value, hashCode));
    }

    public Symbol addConstantLong(long value) {
        return addConstantLongOrDouble(Symbol.CONSTANT_LONG_TAG, value);
    }

    public Symbol addConstantDouble(double value) {
        return addConstantLongOrDouble(Symbol.CONSTANT_DOUBLE_TAG, Double.doubleToRawLongBits(value));
    }

    private Symbol addConstantLongOrDouble(int tag, long value) {
        final int hashCode = hash(tag, value);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == tag && entry.hashCode == hashCode && entry.data == value) {
                return entry;
            }
            entry = entry.next;
        }
        final int index = constantPoolCount;
        constantPool.putByte(tag).putLong(value);
        constantPoolCount += 2;
        return put(new Entry(index, tag, value, hashCode));
    }

    public Symbol addConstant(Object value) {
        if (value instanceof Integer) {
            return addConstantInteger((Integer)value);
        } else if (value instanceof Byte) {
            return addConstantInteger(((Byte) value).intValue());
        } else if (value instanceof Character) {
            return addConstantInteger((Character)value);
        } else if (value instanceof Short) {
            return addConstantInteger(((Short) value).intValue());
        } else if (value instanceof Boolean) {
            return addConstantInteger((Boolean)value ? 1 : 0);
        } else if (value instanceof Float) {
            return addConstantFloat((Float)value);
        } else if (value instanceof Long) {
            return addConstantLong((Long)value);
        } else if (value instanceof Double) {
            return addConstantDouble((Double)value);
        } else if (value instanceof String) {
            return addConstantString((String) value);
        } else if (value instanceof Type) {
            Type type = (Type) value;
            int typeSort = type.getSort();
            if (typeSort == Type.OBJECT) {
                return addConstantClass(type.getInternalName());
            } else if (typeSort == Type.METHOD) {
                return addConstantMethodType(type.getDescriptor());
            } else { // type is a primitive or array type.
                return addConstantClass(type.getDescriptor());
            }
        } else if (value instanceof Handle) {
            Handle handle = (Handle) value;
            return addConstantMethodHandle(
                handle.getTag(),
                handle.getOwner(),
                handle.getName(),
                handle.getDesc(),
                handle.isInterface());
        } else if (value instanceof ConstantDynamic) {
            ConstantDynamic constantDynamic = (ConstantDynamic) value;
            return addConstantDynamic(
                constantDynamic.getName(),
                constantDynamic.getDescriptor(),
                constantDynamic.getBootstrapMethod(),
                ReflectUtils.getConstantDynamicBootstrapMethodArguments(constantDynamic)
            );
        } else {
            throw new IllegalArgumentException("value " + value);
        }
    }

    public Symbol addConstantDynamic(
        final String name,
        final String descriptor,
        final Handle bootstrapMethodHandle,
        final Object... bootstrapMethodArguments) {
        Symbol bootstrapMethod = addBootstrapMethod(bootstrapMethodHandle, bootstrapMethodArguments);
        return addConstantDynamicOrInvokeDynamicReference(
            Symbol.CONSTANT_DYNAMIC_TAG, name, descriptor, bootstrapMethod.index);
    }

    private Symbol addConstantDynamicOrInvokeDynamicReference(
        final int tag, final String name, final String descriptor, final int bootstrapMethodIndex) {
        int hashCode = hash(tag, name, descriptor, bootstrapMethodIndex);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == tag
                && entry.hashCode == hashCode
                && entry.data == bootstrapMethodIndex
                && entry.name.equals(name)
                && entry.value.equals(descriptor)) {
                return entry;
            }
            entry = entry.next;
        }
        put122(tag, bootstrapMethodIndex, addConstantNameAndType(name, descriptor));
        return put(
            new Entry(
                constantPoolCount++, tag, null, name, descriptor, bootstrapMethodIndex, hashCode));
    }

    public int addConstantNameAndType(final String name, final String descriptor) {
        final int tag = Symbol.CONSTANT_NAME_AND_TYPE_TAG;
        int hashCode = hash(tag, name, descriptor);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == tag
                && entry.hashCode == hashCode
                && entry.name.equals(name)
                && entry.value.equals(descriptor)) {
                return entry.index;
            }
            entry = entry.next;
        }
        put122(tag, addConstantUtf8(name), addConstantUtf8(descriptor));
        return put(new Entry(constantPoolCount++, tag, name, descriptor, hashCode)).index;
    }

    private void put122(int byteValue, int shortValue1, int shortValue2) {
        constantPool.putByte(byteValue).putShort(shortValue1).putShort(shortValue2);
    }

    public Symbol addBootstrapMethod(
        final Handle bootstrapMethodHandle, final Object... bootstrapMethodArguments) {
        ByteVector bootstrapMethodsAttribute = bootstrapMethods;
        if (bootstrapMethodsAttribute == null) {
            bootstrapMethodsAttribute = bootstrapMethods = new ByteVector();
        }

        // The bootstrap method arguments can be Constant_Dynamic values, which reference other
        // bootstrap methods. We must therefore add the bootstrap method arguments to the constant pool
        // and BootstrapMethods attribute first, so that the BootstrapMethods attribute is not modified
        // while adding the given bootstrap method to it, in the rest of this method.
        int numBootstrapArguments = bootstrapMethodArguments.length;
        int[] bootstrapMethodArgumentIndexes = new int[numBootstrapArguments];
        for (int i = 0; i < numBootstrapArguments; i++) {
            bootstrapMethodArgumentIndexes[i] = addConstant(bootstrapMethodArguments[i]).index;
        }

        // Write the bootstrap method in the BootstrapMethods table. This is necessary to be able to
        // compare it with existing ones, and will be reverted below if there is already a similar
        // bootstrap method.
        int bootstrapMethodOffset = bootstrapMethodsAttribute.size();
        bootstrapMethodsAttribute.putShort(
            addConstantMethodHandle(
                bootstrapMethodHandle.getTag(),
                bootstrapMethodHandle.getOwner(),
                bootstrapMethodHandle.getName(),
                bootstrapMethodHandle.getDesc(),
                bootstrapMethodHandle.isInterface())
                .index);

        bootstrapMethodsAttribute.putShort(numBootstrapArguments);
        for (int i = 0; i < numBootstrapArguments; i++) {
            bootstrapMethodsAttribute.putShort(bootstrapMethodArgumentIndexes[i]);
        }

        // Compute the length and the hash code of the bootstrap method.
        int bootstrapMethodlength = bootstrapMethodsAttribute.size() - bootstrapMethodOffset;
        int hashCode = bootstrapMethodHandle.hashCode();
        for (Object bootstrapMethodArgument : bootstrapMethodArguments) {
            hashCode ^= bootstrapMethodArgument.hashCode();
        }
        hashCode &= 0x7FFFFFFF;

        // Add the bootstrap method to the symbol table or revert the above changes.
        return addBootstrapMethod(bootstrapMethodOffset, bootstrapMethodlength, hashCode);
    }

    private Symbol addBootstrapMethod(final int offset, final int length, final int hashCode) {
        final byte[] bootstrapMethodsData = ReflectUtils.getByteVectorData(bootstrapMethods);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == Symbol.BOOTSTRAP_METHOD_TAG && entry.hashCode == hashCode) {
                int otherOffset = (int) entry.data;
                boolean isSameBootstrapMethod = true;
                for (int i = 0; i < length; ++i) {
                    if (bootstrapMethodsData[offset + i] != bootstrapMethodsData[otherOffset + i]) {
                        isSameBootstrapMethod = false;
                        break;
                    }
                }
                if (isSameBootstrapMethod) {
                    ReflectUtils.setByteVectorLength(bootstrapMethods, length);
                    return entry;
                }
            }
            entry = entry.next;
        }
        return put(new Entry(bootstrapMethodCount++, Symbol.BOOTSTRAP_METHOD_TAG, offset, hashCode));
    }

    public Symbol addConstantString(final String value) {
        return addConstantUtf8Reference(Symbol.CONSTANT_STRING_TAG, value);
    }

    public Symbol addConstantMethodType(final String methodDescriptor) {
        return addConstantUtf8Reference(Symbol.CONSTANT_METHOD_TYPE_TAG, methodDescriptor);
    }

    public Symbol addConstantMethodHandle(
        final int referenceKind,
        final String owner,
        final String name,
        final String descriptor,
        final boolean isInterface) {
        final int tag = Symbol.CONSTANT_METHOD_HANDLE_TAG;
        // Note that we don't need to include isInterface in the hash computation, because it is
        // redundant with owner (we can't have the same owner with different isInterface values).
        int hashCode = hash(tag, owner, name, descriptor, referenceKind);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == tag
                && entry.hashCode == hashCode
                && entry.data == referenceKind
                && entry.owner.equals(owner)
                && entry.name.equals(name)
                && entry.value.equals(descriptor)) {
                return entry;
            }
            entry = entry.next;
        }
        if (referenceKind <= Opcodes.H_PUTSTATIC) {
            put112(tag, referenceKind, addConstantFieldref(owner, name, descriptor).index);
        } else {
            put112(tag, referenceKind, addConstantMethodref(owner, name, descriptor, isInterface).index);
        }
        return put(new Entry(constantPoolCount++, tag, owner, name, descriptor, referenceKind, hashCode));
    }

    public Symbol addConstantFieldref(final String owner, final String name, final String descriptor) {
        return addConstantMemberReference(Symbol.CONSTANT_FIELDREF_TAG, owner, name, descriptor);
    }

    public Symbol addConstantMethodref(
        final String owner, final String name, final String descriptor, final boolean isInterface) {
        int tag = isInterface ? Symbol.CONSTANT_INTERFACE_METHODREF_TAG : Symbol.CONSTANT_METHODREF_TAG;
        return addConstantMemberReference(tag, owner, name, descriptor);
    }

    private Entry addConstantMemberReference(
        final int tag, final String owner, final String name, final String descriptor) {
        int hashCode = hash(tag, owner, name, descriptor);
        Entry entry = get(hashCode);
        while (entry != null) {
            if (entry.tag == tag
                && entry.hashCode == hashCode
                && entry.owner.equals(owner)
                && entry.name.equals(name)
                && entry.value.equals(descriptor)) {
                return entry;
            }
            entry = entry.next;
        }
        put122(
            tag, addConstantClass(owner).index, addConstantNameAndType(name, descriptor));
        return put(new Entry(constantPoolCount++, tag, owner, name, descriptor, 0, hashCode));
    }

    private void put112(int byteValue1, int byteValue2, int shortValue) {
        constantPool.putByte(byteValue1).putByte(byteValue2).putShort(shortValue);
    }

    private static int hash(int tag, int value) {
        return 0x7fffffff & (tag + value);
    }

    private static int hash(int tag, long value) {
        return 0x7fffffff & (tag + (int)value + (int)(value >>> 32));
    }

    private static int hash(int tag, String value) {
        return 0x7fffffff & (tag + value.hashCode());
    }

    private static int hash(int tag, String value1, String value2) {
        return 0x7fffffff & (tag + value1.hashCode() * value2.hashCode());
    }

    private static int hash(int tag, String value1, String value2, int value3) {
        return 0x7fffffff & (tag + value1.hashCode() * value2.hashCode() * (value3 + 1));
    }

    private static int hash(int tag, String value1, String value2, String value3) {
        return 0x7fffffff & (tag + value1.hashCode() * value2.hashCode() * value3.hashCode());
    }

    private static int hash(int tag, String value1, String value2, String value3, int value4) {
        return 0x7fffffff & (tag + value1.hashCode() * value2.hashCode() * value3.hashCode() * value4);
    }

    private static class Entry extends Symbol {
        final int hashCode;
        Entry next;

        Entry(int index, int tag, String owner, String name, String value, long data, int hashCode) {
            super(index, tag, owner, name, value, data);
            this.hashCode = hashCode;
        }

        Entry(int index, int tag, String value, int hashCode) {
            super(index, tag, null, null, value, 0);
            this.hashCode = hashCode;
        }

        Entry(int index, int tag, String name, String value, int hashCode) {
            super(index, tag, null, name, value, 0);
            this.hashCode = hashCode;
        }

        Entry(int index, int tag, long data, int hashCode) {
            super(index, tag, null, null, null, data);
            this.hashCode = hashCode;
        }
    }
}

package io.github.prcraftmc.classdiff.format;

import io.github.prcraftmc.classdiff.ReflectUtils;
import org.objectweb.asm.ByteVector;

public class SymbolTable {
    private Entry[] entries = new Entry[256];
    private int entryCount = 0;
    private int constantPoolCount = 1;
    private final ByteVector constantPool = new ByteVector();

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

    private static int hash(int tag, String value) {
        return 0x7fffffff & (tag + value.hashCode());
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
    }
}

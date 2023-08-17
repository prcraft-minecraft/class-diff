package io.github.prcraftmc.classdiff.format;

public abstract class Symbol {
    public static final int CONSTANT_CLASS_TAG = 7;
    public static final int CONSTANT_FIELDREF_TAG = 9;
    public static final int CONSTANT_METHODREF_TAG = 10;
    public static final int CONSTANT_INTERFACE_METHODREF_TAG = 11;
    public static final int CONSTANT_STRING_TAG = 8;
    public static final int CONSTANT_INTEGER_TAG = 3;
    public static final int CONSTANT_FLOAT_TAG = 4;
    public static final int CONSTANT_LONG_TAG = 5;
    public static final int CONSTANT_DOUBLE_TAG = 6;
    public static final int CONSTANT_NAME_AND_TYPE_TAG = 12;
    public static final int CONSTANT_UTF8_TAG = 1;
    public static final int CONSTANT_METHOD_HANDLE_TAG = 15;
    public static final int CONSTANT_METHOD_TYPE_TAG = 16;
    public static final int CONSTANT_DYNAMIC_TAG = 17;
    public static final int CONSTANT_INVOKE_DYNAMIC_TAG = 18;
    public static final int CONSTANT_MODULE_TAG = 19;
    public static final int CONSTANT_PACKAGE_TAG = 20;

    public static final int BOOTSTRAP_METHOD_TAG = 64;

    public final int index;
    public final int tag;
    public final String owner;
    public final String name;
    public final String value;
    public final long data;
    public int info;

    protected Symbol(int index, int tag, String owner, String name, String value, long data) {
        this.index = index;
        this.tag = tag;
        this.owner = owner;
        this.name = name;
        this.value = value;
        this.data = data;
    }
}

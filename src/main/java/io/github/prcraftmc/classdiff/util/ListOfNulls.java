package io.github.prcraftmc.classdiff.util;

import java.util.*;

public class ListOfNulls<T> extends AbstractList<T> {
    private int size;

    public ListOfNulls(int size) {
        this.size = size;
    }

    @Override
    public T get(int index) {
        return null;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public T set(int index, T element) {
        if (element != null) {
            throw new IllegalArgumentException();
        }
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return null;
    }

    @Override
    public void add(int index, T element) {
        if (element != null) {
            throw new IllegalArgumentException();
        }
        modCount++;
        size++;
    }

    @Override
    public boolean remove(Object o) {
        if (o == null && size > 0) {
            modCount++;
            size--;
            return true;
        }
        return false;
    }

    @Override
    public int indexOf(Object o) {
        return o != null || size == 0 ? -1 : 0;
    }

    @Override
    public int lastIndexOf(Object o) {
        return o != null ? -1 : size - 1;
    }

    @Override
    public void clear() {
        modCount++;
        size = 0;
    }

    @Override
    public Iterator<T> iterator() {
        return listIterator();
    }
}

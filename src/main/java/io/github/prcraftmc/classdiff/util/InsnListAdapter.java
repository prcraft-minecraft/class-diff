package io.github.prcraftmc.classdiff.util;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;

public class InsnListAdapter extends AbstractList<AbstractInsnNode> {
    private final InsnList list;

    public InsnListAdapter(InsnList list) {
        this.list = list;
    }

    public InsnList getList() {
        return list;
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public AbstractInsnNode get(int index) {
        if (index == 0) {
            return list.getFirst();
        }
        if (index == list.size() - 1) {
            return list.getLast();
        }
        return list.get(index);
    }

    @Override
    public boolean contains(Object o) {
        return o instanceof AbstractInsnNode && list.contains((AbstractInsnNode)o);
    }

    @Override
    public int indexOf(Object o) {
        return o instanceof AbstractInsnNode ? list.indexOf((AbstractInsnNode)o) : -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        return indexOf(o); // An insn can appear at most once
    }

    @Override
    public Iterator<AbstractInsnNode> iterator() {
        return list.iterator();
    }

    @Override
    public ListIterator<AbstractInsnNode> listIterator(int index) {
        return list.iterator(index);
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @Override
    public AbstractInsnNode set(int index, AbstractInsnNode element) {
        throwUnmodifiable();
        final AbstractInsnNode old = list.get(index);
        list.set(old, element);
        return old;
    }

    @Override
    public boolean add(AbstractInsnNode abstractInsnNode) {
        throwUnmodifiable();
        list.add(abstractInsnNode);
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends AbstractInsnNode> c) {
        throwUnmodifiable();
        if (c instanceof InsnListAdapter) {
            list.add(((InsnListAdapter)c).list);
            return true;
        }
        return super.addAll(c);
    }

    @Override
    public void add(int index, AbstractInsnNode element) {
        throwUnmodifiable();
        if (index == 0) {
            list.insert(element);
        } else {
            list.insertBefore(list.get(index), element);
        }
    }

    @Override
    public boolean addAll(int index, Collection<? extends AbstractInsnNode> c) {
        throwUnmodifiable();
        if (c instanceof InsnListAdapter) {
            if (index == 0) {
                list.insert(((InsnListAdapter)c).list);
            } else {
                list.insertBefore(list.get(index), ((InsnListAdapter)c).list);
            }
            return true;
        }
        return super.addAll(index, c);
    }

    @Override
    public boolean remove(Object o) {
        throwUnmodifiable();
        if (!(o instanceof AbstractInsnNode)) {
            return false;
        }
        list.remove((AbstractInsnNode)o);
        return true;
    }

    @Override
    public AbstractInsnNode remove(int index) {
        throwUnmodifiable();
        final AbstractInsnNode old = list.get(index);
        list.remove(old);
        return old;
    }

    @Override
    public void clear() {
        throwUnmodifiable();
        list.clear();
    }

    private void throwUnmodifiable() {
        throw new UnsupportedOperationException("InsnListAdapter is unmodifiable");
    }
}

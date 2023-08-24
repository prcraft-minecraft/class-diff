package io.github.prcraftmc.classdiff.util;

import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import io.github.prcraftmc.classdiff.UncheckedPatchFailure;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.*;
import java.util.function.Supplier;

public class Util {
    public static <T> List<T> getListFromArray(List<T>[] array, int i) {
        if (array == null) {
            return Collections.emptyList();
        }
        final List<T> value = array[i];
        return Util.nullToEmpty(value);
    }

    public static InsnList clone(InsnList list, Map<LabelNode, LabelNode> clonedLabels) {
        final InsnList result = new InsnList();
        for (final AbstractInsnNode insn : list) {
            result.add(insn.clone(clonedLabels));
        }
        return result;
    }

    public static InsnList asInsnList(List<AbstractInsnNode> list) {
        final InsnList result = new InsnList();
        for (final AbstractInsnNode insn : list) {
            result.add(insn);
        }
        return result;
    }

    // This method is a tad slow for now. ReflectUtils needs optimization.
    public static <T> List<T> applyPatchUnchecked(Patch<T> patch, List<T> target) {
        final List<T> result = new ArrayList<>(target);
        final List<AbstractDelta<T>> deltas = patch.getDeltas();
        final ListIterator<AbstractDelta<T>> it = deltas.listIterator(deltas.size());
        try {
            while (it.hasPrevious()) {
                ReflectUtils.invokeAbstractDeltaApplyTo(it.previous(), result);
            }
        } catch (PatchFailedException e) {
            throw new UncheckedPatchFailure(e);
        }
        return result;
    }

    public static <T> Supplier<T> lazy(Supplier<T> initializer) {
        return new Supplier<T>() {
            private volatile boolean initialized;
            private T result;

            @Override
            public T get() {
                if (!initialized) {
                    synchronized (this) {
                        if (!initialized) {
                            result = initializer.get();
                            initialized = true;
                        }
                    }
                }
                return result;
            }
        };
    }

    public static boolean isNullOrEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    public static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}

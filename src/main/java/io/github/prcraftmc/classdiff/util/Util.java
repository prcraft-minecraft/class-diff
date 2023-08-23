package io.github.prcraftmc.classdiff.util;

import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import io.github.prcraftmc.classdiff.UncheckedPatchFailure;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.*;

public class Util {
    public static <T> List<T> getListFromArray(List<T>[] array, int i) {
        if (array == null) {
            return Collections.emptyList();
        }
        final List<T> value = array[i];
        return value != null ? value : Collections.emptyList();
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
}

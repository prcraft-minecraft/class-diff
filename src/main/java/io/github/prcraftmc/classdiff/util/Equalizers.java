package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.tree.InnerClassNode;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

public class Equalizers {
    public static final BiPredicate<InnerClassNode, InnerClassNode> INNER_CLASS_NODE = (a, b) ->
        Objects.equals(a.name, b.name)
            && Objects.equals(a.outerName, b.outerName)
            && Objects.equals(a.innerName, b.innerName)
            && a.access == b.access;

    public static <T> boolean listEquals(List<T> a, List<T> b, BiPredicate<T, T> equalizer) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        final Iterator<T> itA = a.iterator();
        final Iterator<T> itB = b.iterator();
        while (itA.hasNext()) {
            if (!equalizer.test(itA.next(), itB.next())) {
                return false;
            }
        }
        return true;
    }
}

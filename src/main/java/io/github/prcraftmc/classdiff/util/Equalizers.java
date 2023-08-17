package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InnerClassNode;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

public class Equalizers {
    public static boolean innerClass(InnerClassNode a, InnerClassNode b) {
        return Objects.equals(a.name, b.name)
            && Objects.equals(a.outerName, b.outerName)
            && Objects.equals(a.innerName, b.innerName)
            && a.access == b.access;
    }

    public static boolean annotation(AnnotationNode a, AnnotationNode b) {
        if (!Objects.equals(a.desc, b.desc)) {
            return false;
        }
        return listEquals(a.values, b.values, Equalizers::annotationValue);
    }

    @SuppressWarnings("unchecked")
    private static boolean annotationValue(Object a, Object b) {
        if (a instanceof List && b instanceof List) {
            return listEquals((List<Object>)a, (List<Object>)b, Equalizers::annotationValue);
        }
        if (a instanceof String[] && b instanceof String[]) {
            return Arrays.equals((String[])a, (String[])b);
        }
        if (a instanceof AnnotationNode && b instanceof AnnotationNode) {
            return annotation((AnnotationNode)a, (AnnotationNode)b);
        }
        return Objects.equals(a, b);
    }

    public static <T> boolean listEquals(List<T> a, List<T> b, BiPredicate<T, T> equalizer) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
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

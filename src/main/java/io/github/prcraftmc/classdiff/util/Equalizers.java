package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.TypePath;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

public class Equalizers {
    public static boolean innerClass(InnerClassNode a, InnerClassNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.name, b.name)
            && Objects.equals(a.outerName, b.outerName)
            && Objects.equals(a.innerName, b.innerName)
            && a.access == b.access;
    }

    public static boolean annotation(AnnotationNode a, AnnotationNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || !Objects.equals(a.desc, b.desc)) {
            return false;
        }
        return listEquals(a.values, b.values, Equalizers::annotationValue);
    }

    @SuppressWarnings("unchecked")
    private static boolean annotationValue(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
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

    public static boolean typeAnnotation(TypeAnnotationNode a, TypeAnnotationNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.typeRef != b.typeRef || !typePath(a.typePath, b.typePath)) {
            return false;
        }
        return annotation(a, b);
    }

    public static boolean typePath(TypePath a, TypePath b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getLength() != b.getLength()) {
            return false;
        }
        for (int i = 0, l = a.getLength(); i < l; i++) {
            if (a.getStep(i) != b.getStep(i) || a.getStepArgument(i) != b.getStepArgument(i)) {
                return false;
            }
        }
        return true;
    }

    public static <T> boolean listEquals(List<T> a, List<T> b, BiPredicate<T, T> equalizer) {
        if (a == b) {
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

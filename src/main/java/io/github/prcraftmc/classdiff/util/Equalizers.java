package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.tree.*;

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
        if (a == null || b == null) {
            return false;
        }
        if (!Objects.equals(a.desc, b.desc)) {
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
        if (a == null || b == null) {
            return false;
        }
        if (a.typeRef != b.typeRef) {
            return false;
        }
        if (!typePath(a.typePath, b.typePath)) {
            return false;
        }
        return annotation(a, b);
    }

    public static boolean typePath(TypePath a, TypePath b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.getLength() != b.getLength()) {
            return false;
        }
        for (int i = 0, l = a.getLength(); i < l; i++) {
            if (a.getStep(i) != b.getStep(i) || a.getStepArgument(i) != b.getStepArgument(i)) {
                return false;
            }
        }
        return true;
    }

    public static boolean recordComponent(RecordComponentNode a, RecordComponentNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!a.name.equals(b.name)) {
            return false;
        }
        if (!a.descriptor.equals(b.descriptor)) {
            return false;
        }
        if (!Objects.equals(a.signature, b.signature)) {
            return false;
        }
        if (!listEquals(a.visibleAnnotations, b.visibleAnnotations, Equalizers::annotation)) {
            return false;
        }
        if (!listEquals(a.invisibleAnnotations, b.invisibleAnnotations, Equalizers::annotation)) {
            return false;
        }
        if (!listEquals(a.visibleTypeAnnotations, b.visibleTypeAnnotations, Equalizers::typeAnnotation)) {
            return false;
        }
        if (!listEquals(a.invisibleTypeAnnotations, b.invisibleTypeAnnotations, Equalizers::typeAnnotation)) {
            return false;
        }
        return listEquals(a.attrs, b.attrs, Equalizers::attribute);
    }

    public static boolean attribute(Attribute a, Attribute b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!a.type.equals(b.type)) {
            return false;
        }
        return Arrays.equals(ReflectUtils.getAttributeContent(a), ReflectUtils.getAttributeContent(b));
    }

    public static boolean module(ModuleNode a, ModuleNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!Objects.equals(a.name, b.name)) {
            return false;
        }
        if (a.access != b.access) {
            return false;
        }
        if (!Objects.equals(a.version, b.version)) {
            return false;
        }
        if (!Objects.equals(a.mainClass, b.mainClass)) {
            return false;
        }
        if (!Objects.equals(a.packages, b.packages)) {
            return false;
        }
        if (!listEquals(a.requires, b.requires, Equalizers::moduleRequire)) {
            return false;
        }
        if (!listEquals(a.exports, b.exports, Equalizers::moduleExport)) {
            return false;
        }
        if (!listEquals(a.opens, b.opens, Equalizers::moduleOpen)) {
            return false;
        }
        if (!Objects.equals(a.uses, b.uses)) {
            return false;
        }
        return listEquals(a.provides, b.provides, Equalizers::moduleProvide);
    }

    public static boolean moduleRequire(ModuleRequireNode a, ModuleRequireNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!Objects.equals(a.module, b.module)) {
            return false;
        }
        if (a.access != b.access) {
            return false;
        }
        return Objects.equals(a.version, b.version);
    }

    public static boolean moduleExport(ModuleExportNode a, ModuleExportNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!Objects.equals(a.packaze, b.packaze)) {
            return false;
        }
        if (a.access != b.access) {
            return false;
        }
        return Objects.equals(a.modules, b.modules);
    }

    public static boolean moduleOpen(ModuleOpenNode a, ModuleOpenNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!Objects.equals(a.packaze, b.packaze)) {
            return false;
        }
        if (a.access != b.access) {
            return false;
        }
        return Objects.equals(a.modules, b.modules);
    }

    public static boolean moduleProvide(ModuleProvideNode a, ModuleProvideNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!Objects.equals(a.service, b.service)) {
            return false;
        }
        return Objects.equals(a.providers, b.providers);
    }

    public static boolean field(FieldNode a, FieldNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.access != b.access) {
            return false;
        }
        if (!a.name.equals(b.name)) {
            return false;
        }
        if (!a.desc.equals(b.desc)) {
            return false;
        }
        if (!Objects.equals(a.signature, b.signature)) {
            return false;
        }
        if (!Objects.equals(a.value, b.value)) {
            return false;
        }
        if (!listEquals(a.visibleAnnotations, b.visibleAnnotations, Equalizers::annotation)) {
            return false;
        }
        if (!listEquals(a.invisibleAnnotations, b.invisibleAnnotations, Equalizers::annotation)) {
            return false;
        }
        if (!listEquals(a.visibleTypeAnnotations, b.visibleTypeAnnotations, Equalizers::typeAnnotation)) {
            return false;
        }
        if (!listEquals(a.invisibleTypeAnnotations, b.invisibleTypeAnnotations, Equalizers::typeAnnotation)) {
            return false;
        }
        return listEquals(a.attrs, b.attrs, Equalizers::attribute);
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

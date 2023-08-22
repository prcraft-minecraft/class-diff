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
    public static boolean annotationValue(Object a, Object b) {
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

    public static boolean method(MethodNode a, MethodNode b) {
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
        if (!Objects.equals(a.exceptions, b.exceptions)) {
            return false;
        }
        if (!listEquals(a.parameters, b.parameters, Equalizers::parameter)) {
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
        if (!listEquals(a.attrs, b.attrs, Equalizers::attribute)) {
            return false;
        }
        if (!annotationValue(a.annotationDefault, b.annotationDefault)) {
            return false;
        }
        if (a.visibleAnnotableParameterCount != b.visibleAnnotableParameterCount) {
            return false;
        }
        if (!arrayEquals(
            a.visibleParameterAnnotations, b.visibleParameterAnnotations,
            (a1, b1) -> listEquals(a1, b1, Equalizers::annotation)
        )) {
            return false;
        }
        if (a.invisibleAnnotableParameterCount != b.invisibleAnnotableParameterCount) {
            return false;
        }
        if (!arrayEquals(
            a.invisibleParameterAnnotations, b.invisibleParameterAnnotations,
            (a1, b1) -> listEquals(a1, b1, Equalizers::annotation)
        )) {
            return false;
        }
        final LabelMap aMap = new LabelMap(a.instructions);
        final LabelMap bMap = new LabelMap(b.instructions);
        if (!insnList(a.instructions, b.instructions, aMap, bMap)) {
            return false;
        }
        if (!listEquals(a.tryCatchBlocks, b.tryCatchBlocks, tryCatchBlockEqualizer(aMap, bMap))) {
            return false;
        }
        if (a.maxStack != b.maxStack) {
            return false;
        }
        if (a.maxLocals != b.maxLocals) {
            return false;
        }
        if (!listEquals(a.localVariables, b.localVariables, localVariableEqualizer(aMap, bMap))) {
            return false;
        }
        final BiPredicate<LocalVariableAnnotationNode, LocalVariableAnnotationNode> equalizer =
            localVariableAnnotationEqualizer(aMap, bMap);
        if (!listEquals(a.visibleLocalVariableAnnotations, b.visibleLocalVariableAnnotations, equalizer)) {
            return false;
        }
        return listEquals(a.invisibleLocalVariableAnnotations, b.invisibleLocalVariableAnnotations, equalizer);
    }

    public static boolean localVariableAnnotation(
        LocalVariableAnnotationNode a, LocalVariableAnnotationNode b, LabelMap aMap, LabelMap bMap
    ) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        final BiPredicate<LabelNode, LabelNode> equalizer = insnEqualizer(aMap, bMap);
        if (!listEquals(a.start, b.start, equalizer)) {
            return false;
        }
        if (!listEquals(a.end, b.end, equalizer)) {
            return false;
        }
        return a.index.equals(b.index);
    }

    public static BiPredicate<LocalVariableAnnotationNode, LocalVariableAnnotationNode> localVariableAnnotationEqualizer(
        LabelMap aMap, LabelMap bMap
    ) {
        return (a, b) -> localVariableAnnotation(a, b, aMap, bMap);
    }

    public static boolean localVariable(LocalVariableNode a, LocalVariableNode b, LabelMap aMap, LabelMap bMap) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
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
        if (!insn(a.start, b.start, aMap, bMap)) {
            return false;
        }
        if (!insn(a.end, b.end, aMap, bMap)) {
            return false;
        }
        return a.index == b.index;
    }

    public static BiPredicate<LocalVariableNode, LocalVariableNode> localVariableEqualizer(LabelMap aMap, LabelMap bMap) {
        return (a, b) -> localVariable(a, b, aMap, bMap);
    }

    public static boolean tryCatchBlock(TryCatchBlockNode a, TryCatchBlockNode b, LabelMap aMap, LabelMap bMap) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!insn(a.start, b.start, aMap, bMap)) {
            return false;
        }
        if (!insn(a.end, b.end, aMap, bMap)) {
            return false;
        }
        if (!insn(a.handler, b.handler, aMap, bMap)) {
            return false;
        }
        if (!Objects.equals(a.type, b.type)) {
            return false;
        }
        if (!listEquals(a.visibleTypeAnnotations, b.visibleTypeAnnotations, Equalizers::typeAnnotation)) {
            return false;
        }
        return listEquals(a.invisibleTypeAnnotations, b.invisibleTypeAnnotations, Equalizers::typeAnnotation);
    }

    public static BiPredicate<TryCatchBlockNode, TryCatchBlockNode> tryCatchBlockEqualizer(LabelMap aMap, LabelMap bMap) {
        return (a, b) -> tryCatchBlock(a, b, aMap, bMap);
    }

    public static boolean insnList(InsnList a, InsnList b, LabelMap aMap, LabelMap bMap) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        final Iterator<AbstractInsnNode> itA = a.iterator();
        final Iterator<AbstractInsnNode> itB = b.iterator();
        while (itA.hasNext()) {
            if (!insn(itA.next(), itB.next(), aMap, bMap)) {
                return false;
            }
        }
        return true;
    }

    public static boolean insn(AbstractInsnNode a, AbstractInsnNode b, LabelMap aMap, LabelMap bMap) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getType() != b.getType()) {
            return false;
        }
        if (a.getOpcode() != b.getOpcode()) {
            return false;
        }
        if (!listEquals(a.visibleTypeAnnotations, b.visibleTypeAnnotations, Equalizers::typeAnnotation)) {
            return false;
        }
        if (!listEquals(a.invisibleTypeAnnotations, b.invisibleTypeAnnotations, Equalizers::typeAnnotation)) {
            return false;
        }
        switch (a.getType()) {
            case AbstractInsnNode.INSN:
                return true;
            case AbstractInsnNode.INT_INSN:
                return ((IntInsnNode)a).operand == ((IntInsnNode)b).operand;
            case AbstractInsnNode.VAR_INSN:
                return ((VarInsnNode)a).var == ((VarInsnNode)b).var;
            case AbstractInsnNode.TYPE_INSN:
                return ((TypeInsnNode)a).desc.equals(((TypeInsnNode)b).desc);
            case AbstractInsnNode.FIELD_INSN: {
                final FieldInsnNode aNode = (FieldInsnNode)a;
                final FieldInsnNode bNode = (FieldInsnNode)b;
                if (!aNode.owner.equals(bNode.owner)) {
                    return false;
                }
                if (!aNode.name.equals(bNode.name)) {
                    return false;
                }
                return aNode.desc.equals(bNode.desc);
            }
            case AbstractInsnNode.METHOD_INSN: {
                final MethodInsnNode aNode = (MethodInsnNode)a;
                final MethodInsnNode bNode = (MethodInsnNode)b;
                if (!aNode.owner.equals(bNode.owner)) {
                    return false;
                }
                if (!aNode.name.equals(bNode.name)) {
                    return false;
                }
                if (!aNode.desc.equals(bNode.desc)) {
                    return false;
                }
                return aNode.itf == bNode.itf;
            }
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
                final InvokeDynamicInsnNode aNode = (InvokeDynamicInsnNode)a;
                final InvokeDynamicInsnNode bNode = (InvokeDynamicInsnNode)b;
                if (!aNode.name.equals(bNode.name)) {
                    return false;
                }
                if (!aNode.desc.equals(bNode.desc)) {
                    return false;
                }
                if (!aNode.bsm.equals(bNode.bsm)) {
                    return false;
                }
                return Arrays.equals(aNode.bsmArgs, bNode.bsmArgs);
            }
            case AbstractInsnNode.JUMP_INSN:
                return insn(((JumpInsnNode)a).label, ((JumpInsnNode)b).label, aMap, bMap);
            case AbstractInsnNode.LABEL:
                return aMap.getId((LabelNode)a) == bMap.getId((LabelNode)b);
            case AbstractInsnNode.LDC_INSN:
                return ((LdcInsnNode)a).cst.equals(((LdcInsnNode)b).cst);
            case AbstractInsnNode.IINC_INSN: {
                final IincInsnNode aNode = (IincInsnNode)a;
                final IincInsnNode bNode = (IincInsnNode)b;
                if (aNode.var != bNode.var) {
                    return false;
                }
                return aNode.incr == bNode.incr;
            }
            case AbstractInsnNode.TABLESWITCH_INSN: {
                final TableSwitchInsnNode aNode = (TableSwitchInsnNode)a;
                final TableSwitchInsnNode bNode = (TableSwitchInsnNode)b;
                if (aNode.min != bNode.min) {
                    return false;
                }
                if (aNode.max != bNode.max) {
                    return false;
                }
                if (!insn(aNode.dflt, bNode.dflt, aMap, bMap)) {
                    return false;
                }
                return listEquals(aNode.labels, bNode.labels, insnEqualizer(aMap, bMap));
            }
            case AbstractInsnNode.LOOKUPSWITCH_INSN: {
                final LookupSwitchInsnNode aNode = (LookupSwitchInsnNode)a;
                final LookupSwitchInsnNode bNode = (LookupSwitchInsnNode)b;
                if (!insn(aNode.dflt, bNode.dflt, aMap, bMap)) {
                    return false;
                }
                if (!aNode.keys.equals(bNode.keys)) {
                    return false;
                }
                return listEquals(aNode.labels, bNode.labels, insnEqualizer(aMap, bMap));
            }
            case AbstractInsnNode.FRAME: {
                final FrameNode aNode = (FrameNode)a;
                final FrameNode bNode = (FrameNode)b;
                if (aNode.type != bNode.type) {
                    return false;
                }
                final BiPredicate<Object, Object> equalizer = frameObjectTypeEqualizer(aMap, bMap);
                if (!listEquals(aNode.local, bNode.local, equalizer)) {
                    return false;
                }
                return listEquals(aNode.stack, bNode.stack, equalizer);
            }
            case AbstractInsnNode.LINE: {
                final LineNumberNode aNode = (LineNumberNode)a;
                final LineNumberNode bNode = (LineNumberNode)b;
                if (aNode.line != bNode.line) {
                    return false;
                }
                return insn(aNode.start, bNode.start, aMap, bMap);
            }
            default:
                throw new IllegalArgumentException("Unknown insn type: " + a.getType());
        }
    }

    public static boolean frameObjectType(Object a, Object b, LabelMap aMap, LabelMap bMap) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getClass() != b.getClass()) {
            return false;
        }
        if (a instanceof Integer || a instanceof String) {
            return Objects.equals(a, b);
        }
        if (!(a instanceof LabelNode)) {
            throw new IllegalArgumentException(a.getClass() + " is not a supported frame object type!");
        }
        return insn((LabelNode)a, (LabelNode)b, aMap, bMap);
    }

    public static BiPredicate<Object, Object> frameObjectTypeEqualizer(LabelMap aMap, LabelMap bMap) {
        return (a, b) -> frameObjectType(a, b, aMap, bMap);
    }

    public static <T extends AbstractInsnNode> BiPredicate<T, T> insnEqualizer(LabelMap aMap, LabelMap bMap) {
        return (a, b) -> insn(a, b, aMap, bMap);
    }

    public static boolean parameter(ParameterNode a, ParameterNode b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!a.name.equals(b.name)) {
            return false;
        }
        return a.access == b.access;
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

    public static <T> boolean arrayEquals(T[] a, T[] b, BiPredicate<T, T> equalizer) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!equalizer.test(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }
}

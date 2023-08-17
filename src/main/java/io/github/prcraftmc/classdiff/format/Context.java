package io.github.prcraftmc.classdiff.format;

import org.objectweb.asm.Label;
import org.objectweb.asm.TypePath;

class Context {
    Label[] currentMethodLabels;
    int currentTypeAnnotationTarget;
    TypePath currentTypeAnnotationTargetPath;
    Label[] currentLocalVariableAnnotationRangeStarts;
    Label[] currentLocalVariableAnnotationRangeEnds;
    int[] currentLocalVariableAnnotationRangeIndices;
}

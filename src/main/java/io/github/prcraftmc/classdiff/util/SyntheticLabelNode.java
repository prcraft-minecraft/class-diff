package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.tree.LabelNode;

public class SyntheticLabelNode extends LabelNode {
    private final int id;

    public SyntheticLabelNode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}

package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

public class LabelMap {
    private final LabelNode[] byId;
    private final Map<LabelNode, Integer> toId;

    public LabelMap(LabelNode... labels) {
        byId = labels;
        toId = new HashMap<>(labels.length);
        for (final LabelNode label : labels) {
            toId.put(label, toId.size());
        }
    }

    public LabelMap(Iterable<AbstractInsnNode> insns) {
        this(StreamSupport.stream(insns.spliterator(), false)
            .filter(i -> i instanceof LabelNode)
            .map(i -> (LabelNode)i)
            .toArray(LabelNode[]::new)
        );
    }

    public LabelNode byId(int id) {
        return byId[id];
    }

    public int getId(LabelNode label) {
        return toId.get(label);
    }
}

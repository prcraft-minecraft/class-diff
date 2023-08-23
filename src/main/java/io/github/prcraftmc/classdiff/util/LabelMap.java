package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;

public class LabelMap implements Iterable<LabelNode> {
    public static final LabelMap EMPTY = new LabelMap();

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

    public int size() {
        return byId.length;
    }

    public LabelNode byId(int id) {
        return byId[id];
    }

    public int getId(LabelNode label) {
        if (label instanceof SyntheticLabelNode) {
            return ((SyntheticLabelNode)label).getId();
        }
        return toId.get(label);
    }

    @Override
    public Iterator<LabelNode> iterator() {
        return new Iterator<LabelNode>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < size();
            }

            @Override
            public LabelNode next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return byId(index++);
            }
        };
    }
}

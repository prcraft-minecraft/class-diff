package io.github.prcraftmc.classdiff.util;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.RecordComponentNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MemberName {
    public final String name;
    public final String descriptor;

    public MemberName(String name, String descriptor) {
        this.name = name;
        this.descriptor = descriptor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberName)) return false;
        MemberName that = (MemberName)o;
        return Objects.equals(name, that.name) && Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, descriptor);
    }

    public static MemberName fromRecordComponent(RecordComponentNode node) {
        return new MemberName(node.name, node.descriptor);
    }

    public static List<MemberName> fromRecordComponents(List<RecordComponentNode> nodes) {
        if (nodes == null) {
            return Collections.emptyList();
        }
        final List<MemberName> result = new ArrayList<>(nodes.size());
        for (final RecordComponentNode node : nodes) {
            result.add(fromRecordComponent(node));
        }
        return result;
    }

    public static MemberName fromField(FieldNode node) {
        return new MemberName(node.name, node.desc);
    }

    public static List<MemberName> fromFields(List<FieldNode> nodes) {
        if (nodes == null) {
            return Collections.emptyList();
        }
        final List<MemberName> result = new ArrayList<>(nodes.size());
        for (final FieldNode node : nodes) {
            result.add(fromField(node));
        }
        return result;
    }
}

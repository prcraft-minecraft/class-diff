package io.github.prcraftmc.classdiff.util;

import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.objectweb.asm.ByteVector;

import java.util.List;
import java.util.function.BiConsumer;

public class PatchWriter<T> {
    private final BiConsumer<ByteVector, T> writer;

    public PatchWriter(BiConsumer<ByteVector, T> writer) {
        this.writer = writer;
    }

    public void write(ByteVector vector, List<AbstractDelta<T>> patch) {
        vector.putShort(patch.size());
        for (final AbstractDelta<T> delta : patch) {
            vector.putByte(delta.getType().ordinal());
            switch (delta.getType()) {
                case CHANGE:
                    vector.putShort(delta.getSource().getPosition());
                    vector.putShort(delta.getSource().size());
                    vector.putShort(delta.getTarget().size());
                    for (final T line : delta.getTarget().getLines()) {
                        writer.accept(vector, line);
                    }
                    break;
                case DELETE:
                    vector.putShort(delta.getSource().getPosition());
                    vector.putShort(delta.getSource().size());
                    break;
                case INSERT:
                    vector.putShort(delta.getSource().getPosition());
                    vector.putShort(delta.getTarget().size());
                    for (final T line : delta.getTarget().getLines()) {
                        writer.accept(vector, line);
                    }
                    break;
                case EQUAL:
                    // EQUAL does nothing
                    break;
            }
        }
    }

    public void write(ByteVector vector, Patch<T> patch) {
        write(vector, patch.getDeltas());
    }
}

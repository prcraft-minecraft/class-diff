package io.github.prcraftmc.classdiff.util;

import com.github.difflib.patch.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PatchReader<T> {
    private final Function<ByteReader, T> reader;

    public PatchReader(Function<ByteReader, T> reader) {
        this.reader = reader;
    }

    private void read(int deltaCount, ByteReader input, Consumer<AbstractDelta<T>> output, List<T> originals) {
        for (int i = 0; i < deltaCount; i++) {
            final DeltaType type = DeltaType.values()[input.readByte()];
            switch (type) {
                case CHANGE: {
                    final int position = input.readShort();
                    final int sourceLength = input.readShort();
                    final int targetLength = input.readShort();
                    final List<T> targetLines = new ArrayList<>(targetLength);
                    for (int j = 0; j < targetLength; j++) {
                        targetLines.add(reader.apply(input));
                    }
                    output.accept(new ChangeDelta<>(
                        new Chunk<>(position, originals.subList(position, position + sourceLength)),
                        new Chunk<>(0, targetLines)
                    ));
                    break;
                }
                case DELETE: {
                    final int position = input.readShort();
                    final int length = input.readShort();
                    output.accept(new DeleteDelta<>(
                        new Chunk<>(position, originals.subList(position, position + length)),
                        new Chunk<>(0, Collections.emptyList())
                    ));
                    break;
                }
                case INSERT: {
                    final int position = input.readShort();
                    final int targetLength = input.readShort();
                    final List<T> targetLines = new ArrayList<>(targetLength);
                    for (int j = 0; j < targetLength; j++) {
                        targetLines.add(reader.apply(input));
                    }
                    output.accept(new InsertDelta<>(
                        new Chunk<>(position, Collections.emptyList()),
                        new Chunk<>(0, targetLines)
                    ));
                    break;
                }
                case EQUAL:
                    // EQUAL does nothing
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    public List<AbstractDelta<T>> readDeltaList(ByteReader input, List<T> originals) {
        final int deltaCount = input.readShort();
        final List<AbstractDelta<T>> result = new ArrayList<>(deltaCount);
        read(deltaCount, input, result::add, originals);
        return result;
    }

    public Patch<T> readPatch(ByteReader input, List<T> originals) {
        final int deltaCount = input.readShort();
        final Patch<T> result = new Patch<>(deltaCount);
        read(deltaCount, input, result::addDelta, originals);
        return result;
    }
}

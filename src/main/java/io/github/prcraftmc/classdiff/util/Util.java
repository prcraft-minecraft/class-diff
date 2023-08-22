package io.github.prcraftmc.classdiff.util;

import java.util.Collections;
import java.util.List;

public class Util {
    public static <T> List<T> getListFromArray(List<T>[] array, int i) {
        if (array == null) {
            return Collections.emptyList();
        }
        final List<T> value = array[i];
        return value != null ? value : Collections.emptyList();
    }
}

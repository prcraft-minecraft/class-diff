package io.github.prcraftmc.classdiff;

import com.github.difflib.patch.PatchFailedException;

public class UncheckedPatchFailure extends RuntimeException {
    public UncheckedPatchFailure(PatchFailedException e) {
        super(e.getMessage(), e);
    }
}

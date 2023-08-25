package io.github.prcraftmc.classdiff.cli;

import net.sourceforge.argparse4j.annotation.Arg;

import java.nio.file.Path;

public class Options {
    @Arg
    public Action action;

    @Arg(dest = "skip_debug")
    public boolean skipDebug;

    @Arg(dest = "skip_unknown_attributes")
    public boolean skipUnknownAttributes;

    @Arg
    public Path source;

    @Arg
    public Path target;

    public enum Action {
        TEST,
        DIFF
    }
}

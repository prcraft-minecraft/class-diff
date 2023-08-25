package io.github.prcraftmc.classdiff.cli;

import net.sourceforge.argparse4j.annotation.Arg;
import org.fusesource.jansi.Ansi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

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

    @Arg
    public Path output;

    @Arg
    public Path patch;

    public Path getOutput(Function<Options, Path> defaultResolve) {
        Path output = this.output;
        if (output == null) {
            output = defaultResolve.apply(this);
        }
        if (output.getParent() != null) {
            try {
                Files.createDirectories(output.getParent());
            } catch (Exception e) {
                System.err.println(Ansi.ansi()
                    .fgBrightYellow()
                    .a("WARN: Failed to create parent directories of file ").a(output)
                    .a('\n').a(e)
                    .reset()
                );
            }
        }
        return output;
    }

    public enum Action {
        DIFF,
        APPLY,
        TEST,
    }
}

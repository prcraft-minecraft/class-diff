package io.github.prcraftmc.classdiff.cli;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.ClassDiffer;
import io.github.prcraftmc.classdiff.ClassPatcher;
import io.github.prcraftmc.classdiff.format.DiffReader;
import io.github.prcraftmc.classdiff.format.DiffWriter;
import io.github.prcraftmc.classdiff.util.Util;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.ArgumentType;
import net.sourceforge.argparse4j.inf.Subparser;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClassDiffCli {
    @SuppressWarnings("DuplicateExpressions")
    private static final ArgumentType<Path> PATH_ARGUMENT_TYPE = (parser, arg, value) -> {
        try {
            return Paths.get(value);
        } catch (InvalidPathException e) {
            final URI uri;
            try {
                uri = new URI(value);
            } catch (URISyntaxException e1) {
                throw new ArgumentParserException("Invalid path: " + e.getLocalizedMessage(), e, parser, arg);
            }
            try {
                return Paths.get(uri);
            } catch (IllegalArgumentException e1) {
                throw new ArgumentParserException("Invalid path for URI path: " + e1.getLocalizedMessage(), e1, parser, arg);
            } catch (FileSystemNotFoundException e1) {
                try {
                    FileSystems.newFileSystem(uri, Collections.emptyMap());
                    return Paths.get(uri);
                } catch (IOException e2) {
                    throw new ArgumentParserException("Failed to open URI path: " + e2.getLocalizedMessage(), e2, parser, arg);
                }
            }
        }
    };

    public static void main(String[] args) throws Exception {
        final ArgumentParser parser = ArgumentParsers.newFor("class-diff")
            .fromFilePrefix("@")
            .build()
            .defaultHelp(true)
            .description("CLI tool for class-diff, a library for creating diffs between JVM .class files.");
        parser.addArgument("--skip-debug", "-D")
            .help("Skip debug data. This skips: source file data, parameter metadata, local variable data, and line number data.")
            .action(Arguments.storeTrue()); // Why is this in impl? Weird.
        parser.addArgument("--skip-unknown-attributes", "-A")
            .help(
                "Skip unknown attributes. This is useful for when unknown attributes (such as those used by modules) " +
                    "will be left invalid after patching due to the constant pool being in a different order."
            )
            .action(Arguments.storeTrue());

        final Subparser test = parser.addSubparsers()
            .addParser("test")
            .setDefault("action", Options.Action.TEST);
        test.addArgument("source")
            .type(PATH_ARGUMENT_TYPE)
            .help("Source file to diff from");
        test.addArgument("target")
            .type(PATH_ARGUMENT_TYPE)
            .help("Modified file to diff with");

        final Options options = new Options();
        try {
            parser.parseArgs(args, options);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        AnsiConsole.systemInstall();
        switch (options.action) {
            case TEST:
                test(options);
                break;
        }
    }

    public static void test(Options options) throws Exception {
        final ClassNode source = readClass(options, options.source);
        final ClassNode target = readClass(options, options.target);
        tryClose(options.source, options.target);

        final DiffWriter writer = new DiffWriter();
        ClassDiffer.diff(source, target, writer);

        final byte[] result = writer.toByteArray();
        ClassPatcher.patch(source, new DiffReader(result));

        printDiff(target, source, "expect.txt", "actual.txt");
    }

    private static void tryClose(Path... paths) throws IOException {
        for (final Path path : paths) {
            tryClose(path);
        }
    }

    private static void tryClose(Path path) throws IOException {
        try {
            path.getFileSystem().close();
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static void printDiff(ClassNode nodeA, ClassNode nodeB, String fileA, String fileB) {
        final List<String> linesA = classNodeToLines(nodeA);
        final List<String> linesB = classNodeToLines(nodeB);

        final Patch<String> diff = DiffUtils.diff(linesA, linesB);
        final List<String> outLines = UnifiedDiffUtils.generateUnifiedDiff(fileA, fileB, linesA, diff, 3);

        for (final String line : outLines) {
            if (line.startsWith("-")) {
                System.out.println(Ansi.ansi().fgBrightRed().a(line).reset());
            } else if (line.startsWith("+")) {
                System.out.println(Ansi.ansi().fgBrightGreen().a(line).reset());
            } else if (line.startsWith("@")) {
                System.out.println(Ansi.ansi().fgCyan().a(line).reset());
            } else {
                System.out.println(line);
            }
        }

        System.out.println();
        if (diff.getDeltas().isEmpty()) {
            System.out.println(Ansi.ansi().fgBrightGreen().a("\u2714 Test success!").reset());
        } else {
            System.out.println(Ansi.ansi().fgBrightRed().a("\u274c Test failure!").reset());
        }
    }

    private static List<String> classNodeToLines(ClassNode classNode) {
        return Arrays.asList(classNodeToString(classNode).split("\n"));
    }

    private static String classNodeToString(ClassNode classNode) {
        final StringWriter result = new StringWriter();
        classNode.accept(new TraceClassVisitor(new PrintWriter(result)));
        return result.toString();
    }

    private static ClassNode readClass(Options options, Path path) throws IOException {
        final ClassReader reader;
        try (InputStream is = Files.newInputStream(path)) {
            reader = new ClassReader(is);
        }

        int parsingOptions = 0;
        if (options.skipDebug) {
            parsingOptions |= ClassReader.SKIP_DEBUG;
        }

        final ClassNode result = new ClassNode();
        reader.accept(result, parsingOptions);

        if (options.skipUnknownAttributes) {
            stripUnknownAttrs(result);
        }
        return result;
    }

    private static void stripUnknownAttrs(ClassNode node) {
        node.attrs = stripUnknownAttrs(node.attrs);
        for (final RecordComponentNode recordComponent : Util.nullToEmpty(node.recordComponents)) {
            recordComponent.attrs = stripUnknownAttrs(recordComponent.attrs);
        }
        for (final FieldNode field : node.fields) {
            field.attrs = stripUnknownAttrs(field.attrs);
        }
        for (final MethodNode method : node.methods) {
            method.attrs = stripUnknownAttrs(method.attrs);
        }
    }

    private static List<Attribute> stripUnknownAttrs(List<Attribute> attrs) {
        if (attrs == null) {
            return null;
        }
        attrs.removeIf(a -> a.getClass() == Attribute.class);
        return !attrs.isEmpty() ? attrs : null;
    }
}

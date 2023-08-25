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
import net.sourceforge.argparse4j.inf.Subparser;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ClassDiffCli {
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

        final Subparser diff = parser.addSubparsers()
            .addParser("diff")
            .help("Generate a patch between two class files")
            .setDefault("action", Options.Action.DIFF);
        diff.addArgument("source")
            .type(new PathArgumentType(true))
            .help("Source file to diff from");
        diff.addArgument("target")
            .type(new PathArgumentType(true))
            .help("Modified file to diff with");
        diff.addArgument("output")
            .type(new PathArgumentType(false))
            .help("Target file to output to")
            .nargs("?");

        final Subparser apply = parser.addSubparsers()
            .addParser("apply")
            .help("Apply a patch to a class file")
            .setDefault("action", Options.Action.APPLY);
        apply.addArgument("source")
            .type(new PathArgumentType(true))
            .help("Source file to patch");
        apply.addArgument("patch")
            .type(new PathArgumentType(true))
            .help("Patch file to apply");
        apply.addArgument("output")
            .type(new PathArgumentType(false))
            .help("Target file to output to")
            .nargs("?");

        final Subparser print = parser.addSubparsers()
            .addParser("print")
            .help("Print information about things");
        print.addArgument("--code-form", "-c")
            .help("Print information in code form")
            .action(Arguments.storeTrue());

        final Subparser printClass = print.addSubparsers()
            .addParser("class")
            .help("Print out the textual representation of a class")
            .setDefault("action", Options.Action.PRINT_CLASS);
        printClass.addArgument("class")
            .type(new PathArgumentType(true))
            .help("Class file to print");

        final Subparser printChanges = print.addSubparsers()
            .addParser("changes")
            .help("Print out the textual representation of a class")
            .setDefault("action", Options.Action.PRINT_CHANGES);
        printChanges.addArgument("source")
            .type(new PathArgumentType(true))
            .help("Source file to diff from");
        printChanges.addArgument("target")
            .type(new PathArgumentType(true))
            .help("Modified file to diff with");

        final Subparser test = parser.addSubparsers()
            .addParser("test")
            .help("Test class-diff's ability to diff and patch files")
            .setDefault("action", Options.Action.TEST);
        test.addArgument("--code-form", "-c")
            .help("Print information in code form")
            .action(Arguments.storeTrue());
        test.addArgument("source")
            .type(new PathArgumentType(true))
            .help("Source file to diff from");
        test.addArgument("target")
            .type(new PathArgumentType(true))
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
            case DIFF:
                diff(options);
                break;
            case APPLY:
                apply(options);
                break;
            case PRINT_CLASS:
                printClass(options);
                break;
            case PRINT_CHANGES:
                printChanges(options);
                break;
            case TEST:
                test(options);
                break;
            default:
                throw new UnsupportedOperationException("Action " + options.action + " not implemented yet");
        }
    }

    public static void diff(Options options) throws Exception {
        final Path output = options.getOutput(o -> {
            final String targetFilename = o.target.getFileName().toString();
            final int dotIndex = targetFilename.lastIndexOf('.');
            final String strippedFilename = dotIndex >= 0 ? targetFilename.substring(0, dotIndex) : targetFilename;
            return o.target.getParent().resolve(strippedFilename + ".cdiff");
        });

        final ClassNode source = readClass(options, options.source);
        final ClassNode target = readClass(options, options.target);

        final DiffWriter writer = new DiffWriter();
        ClassDiffer.diff(source, target, writer);
        try {
            Files.write(output, writer.toByteArray());
        } catch (IOException e) {
            System.err.println(Ansi.ansi()
                .fgBrightRed()
                .a("Failed to write to file ").a(output)
                .a('\n').a(e)
                .reset()
            );
            System.exit(1);
        }

        System.out.println("Patch written to " + output);
        tryClose(options.source, options.target, output);
    }

    public static void apply(Options options) throws Exception {
        final ClassNode clazz = readClass(options, options.source);
        final byte[] patch = Files.readAllBytes(options.patch);

        final String originalClassName = clazz.name;
        final int slashIndex = originalClassName.lastIndexOf('/');
        final String originalPackage = slashIndex > 0 ? originalClassName.substring(0, slashIndex) : "";

        ClassPatcher.patch(clazz, new DiffReader(patch));

        final Path output = options.getOutput(o -> {
            Path result = o.source.getParent();
            if (result != null) {
                if (!originalPackage.isEmpty()) {
                    result = unresolve(result, originalPackage);
                    result = result != null
                        ? result.resolve(clazz.name + ".class")
                        : o.source.getFileSystem().getPath(clazz.name + ".class");
                }
                if (!result.equals(o.source.toAbsolutePath()) && !result.getFileSystem().isReadOnly()) {
                    return result;
                }
            }
            final String targetName = clazz.name.substring(clazz.name.lastIndexOf('/') + 1) + ".class";
            result = o.patch.getParent();
            if (result != null) {
                return result.resolve(targetName);
            }
            return o.patch.getFileSystem().getPath(targetName);
        });

        final ClassWriter writer = new ClassWriter(0);
        clazz.accept(writer);
        try {
            Files.write(output, writer.toByteArray());
        } catch (Exception e) {
            System.err.println(Ansi.ansi()
                .fgBrightRed()
                .a("Failed to write to file ").a(output)
                .a('\n').a(e)
                .reset()
            );
            System.exit(1);
        }

        System.out.println("Patched class written to " + output);
        tryClose(options.source, options.patch, output);
    }

    public static void printClass(Options options) throws Exception {
        System.out.println(classNodeToString(readClass(options, options.clazz), options));
    }

    public static void printChanges(Options options) throws Exception {
        final ClassNode source = readClass(options, options.source);
        final ClassNode target = readClass(options, options.target);
        printDiff(
            source, target,
            source.name.substring(source.name.lastIndexOf('/')) + ".txt",
            target.name.substring(target.name.lastIndexOf('/')) + ".txt",
            options
        );
    }

    public static void test(Options options) throws Exception {
        final ClassNode source = readClass(options, options.source);
        final ClassNode target = readClass(options, options.target);
        final ClassNode input = readClass(options, options.source);
        tryClose(options.source, options.target);

        final DiffWriter writer = new DiffWriter();
        ClassDiffer.diff(source, target, writer);

        final byte[] result = writer.toByteArray();
        ClassPatcher.patch(input, new DiffReader(result));

        final Patch<String> diff = printDiff(target, input, "expect.txt", "actual.txt", options);

        if (diff.getDeltas().isEmpty()) {
            System.out.println(Ansi.ansi().fgBrightGreen().a("\u2714 Test success!").reset());
        } else {
            System.out.println(Ansi.ansi().fgBrightRed().a("\u274c Test failure!").reset());
        }
    }

    private static Path unresolve(Path start, String path) {
        Path result = start.toAbsolutePath();
        int i = path.lastIndexOf('/');
        int lastI = path.length();
        while (true) {
            final String element = path.substring(i + 1, lastI);
            if (result.getFileName().toString().equals(element)) {
                result = result.getParent();
                if (result == null) break;
            }
            if (i == -1) break;
            lastI = i;
            i = path.lastIndexOf('/', i - 1);
        }
        return result;
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

    private static Patch<String > printDiff(ClassNode nodeA, ClassNode nodeB, String fileA, String fileB, Options options) {
        final List<String> linesA = classNodeToLines(nodeA, options);
        final List<String> linesB = classNodeToLines(nodeB, options);

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

        return diff;
    }

    private static List<String> classNodeToLines(ClassNode classNode, Options options) {
        return Arrays.asList(classNodeToString(classNode, options).split("\n"));
    }

    private static String classNodeToString(ClassNode classNode, Options options) {
        final StringWriter result = new StringWriter();
        classNode.accept(new TraceClassVisitor(
            null, options.codeForm ? new ASMifier() : new Textifier(), new PrintWriter(result)
        ));
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

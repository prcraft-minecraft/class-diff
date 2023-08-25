package io.github.prcraftmc.classdiff.cli;

import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.ArgumentType;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class PathArgumentType implements ArgumentType<Path> {
    private final boolean checkRead;

    public PathArgumentType(boolean checkRead) {
        this.checkRead = checkRead;
    }

    @Override
    @SuppressWarnings("DuplicateExpressions")
    public Path convert(ArgumentParser parser, Argument arg, String value) throws ArgumentParserException {
        Path path;
        try {
            path = Paths.get(value);
        } catch (InvalidPathException e) {
            final URI uri;
            try {
                uri = new URI(value);
            } catch (URISyntaxException e1) {
                throw new ArgumentParserException("Invalid path: " + e.getLocalizedMessage(), e, parser, arg);
            }
            try {
                path = Paths.get(uri);
            } catch (IllegalArgumentException e1) {
                throw new ArgumentParserException("Invalid path for URI path: " + e1.getLocalizedMessage(), e1, parser, arg);
            } catch (FileSystemNotFoundException e1) {
                try {
                    FileSystems.newFileSystem(uri, Collections.emptyMap());
                    path = Paths.get(uri);
                } catch (IOException e2) {
                    throw new ArgumentParserException("Failed to open URI path: " + e2.getLocalizedMessage(), e2, parser, arg);
                }
            }
        }
        if (checkRead) {
            try {
                path.getFileSystem().provider().checkAccess(path, AccessMode.READ);
            } catch (IOException e) {
                throw new ArgumentParserException(
                    "Unable to read " + path + " (do you have permission to access this file?)\n" + e, e, parser, arg
                );
            }
        }
        return path;
    }

    private static String flagsToString(AccessMode... access) {
        if (access.length == 0) {
            return "<any>";
        }
        final Set<AccessMode> modes = EnumSet.noneOf(AccessMode.class);
        Collections.addAll(modes, access);

        final StringBuilder result = new StringBuilder(3);
        if (modes.contains(AccessMode.READ)) {
            result.append('r');
        }
        if (modes.contains(AccessMode.WRITE)) {
            result.append('w');
        }
        if (modes.contains(AccessMode.EXECUTE)) {
            result.append('x');
        }
        return result.toString();
    }
}

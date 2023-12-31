package io.github.prcraftmc.classdif.test;

import io.github.prcraftmc.classdiff.ClassDiffer;
import io.github.prcraftmc.classdiff.ClassPatcher;
import io.github.prcraftmc.classdiff.format.DiffReader;
import io.github.prcraftmc.classdiff.format.DiffWriter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassTest {
    private void test(String file1, String file2) throws IOException {
        test(ClassTest.class.getResourceAsStream(file1), ClassTest.class.getResourceAsStream(file2));
    }

    private void test(InputStream file1, InputStream file2) throws IOException {
        final ClassNode helloNode = new ClassNode();
        final ClassNode worldNode = new ClassNode();
        new ClassReader(file1).accept(helloNode, 0);
        new ClassReader(file2).accept(worldNode, 0);

        final DiffWriter writer = new DiffWriter();
        ClassDiffer.diff(helloNode, worldNode, writer);

        final byte[] result = writer.toByteArray();
        System.out.println(new String(result, StandardCharsets.ISO_8859_1));

        ClassPatcher.patch(helloNode, new DiffReader(result));

        assertEquals(
            toString(worldNode),
            toString(helloNode)
        );
    }

    private String toString(ClassNode node) {
        final StringWriter result = new StringWriter();
        node.accept(new TraceClassVisitor(new PrintWriter(result)));
        return result.toString();
    }

    @Test
    public void test1() throws IOException {
        test("test1/Hello.class", "test1/World.class");
    }

    @Test
    public void test2() throws IOException {
        test("test2/Hello.class", "test2/World.class");
    }

    @Test
    public void test3() throws IOException {
        final Enumeration<URL> resources = ClassTest.class.getClassLoader().getResources("module-info.class");
        test(
            resources.nextElement().openStream(),
            resources.nextElement().openStream()
        );
    }

    @Test
    public void test4() throws IOException {
        test("test4/Hello.class", "test4/World.class");
    }

    @Test
    public void test5() throws IOException {
        test("test5/Hello.class", "test5/World.class");
    }

    @Test
    public void test6() throws IOException {
        test("test6/Hello.class", "test6/World.class");
    }

    @Test
    public void test7() throws IOException {
        test("test7/Hello.class", "test7/World.class");
    }

    @Test
    public void test8() throws IOException {
        test("/java/lang/String.class", "/java/lang/Class.class");
    }

    @Test
    public void test9() throws IOException {
        test("/java/lang/Integer.class", "/java/lang/Float.class");
    }

    @Test
    public void test10() throws IOException {
        test("/java/lang/Object.class", "/java/lang/Record.class");
    }
}

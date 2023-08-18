package test3;

import io.github.prcraftmc.classdiff.ClassDiffer;
import io.github.prcraftmc.classdiff.ClassPatcher;
import io.github.prcraftmc.classdiff.format.DiffReader;
import io.github.prcraftmc.classdiff.format.DiffWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class Test {
    public static void main(String[] args) throws Exception {
        final Enumeration<URL> resources = Test.class.getClassLoader().getResources("module-info.class");

        final ClassNode aNode = new ClassNode();
        final ClassNode bNode = new ClassNode();
        new ClassReader(resources.nextElement().openStream()).accept(aNode, 0);
        new ClassReader(resources.nextElement().openStream()).accept(bNode, 0);

        final DiffWriter writer = new DiffWriter();
        ClassDiffer.diff(aNode, bNode, writer);

        final byte[] result = writer.toByteArray();
        System.out.println(new String(result, StandardCharsets.ISO_8859_1));

        ClassPatcher.patch(aNode, new DiffReader(result));
    }
}

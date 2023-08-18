package test1;

import io.github.prcraftmc.classdiff.ClassDiffer;
import io.github.prcraftmc.classdiff.ClassPatcher;
import io.github.prcraftmc.classdiff.format.DiffReader;
import io.github.prcraftmc.classdiff.format.DiffWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

public class Test {
    @SuppressWarnings("DataFlowIssue")
    public static void main(String[] args) throws Exception {
        final ClassNode helloNode = new ClassNode();
        final ClassNode worldNode = new ClassNode();
        new ClassReader(Test.class.getResourceAsStream("/test1/Hello.class")).accept(helloNode, 0);
        new ClassReader(Test.class.getResourceAsStream("/test1/World.class")).accept(worldNode, 0);

        final DiffWriter writer = new DiffWriter();
        ClassDiffer.diff(helloNode, worldNode, writer);

        final byte[] result = writer.toByteArray();
        System.out.println(new String(result, StandardCharsets.ISO_8859_1));

        ClassPatcher.patch(helloNode, new DiffReader(result));
    }

    @Target({ElementType.TYPE, ElementType.TYPE_USE})
    public @interface Inner {
    }
}

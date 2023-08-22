package io.github.prcraftmc.classdif.test.test5;

public class Hello {
    public static void test(@Test.Inner int a) {
        System.out.println(a);
    }

    public static void test2(@Test.Inner(72) float b) {
        System.out.println(b);
    }
}

package io.github.prcraftmc.classdif.test.test6;

import io.github.prcraftmc.classdif.test.test1.Test;

public class World {
    public static void main(String[] args) {
        try {
            @Test.Inner final String hi = "hello";
            System.out.println(hi);
        } catch (@Test.Inner Exception ignored) {
            final String myString = (@Test.Inner String)keepCast();
            System.out.println(myString);
        }
    }

    private static Object keepCast() {
        return "world";
    }
}

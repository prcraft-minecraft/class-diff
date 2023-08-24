package io.github.prcraftmc.classdif.test.test6;



public class Hello {
    public static void main(String[] args) {
        try {
            final String hi = "hello";
            System.out.println(hi);
        } catch (Exception ignored) {
            final String myString = (String)keepCast();
            System.out.println(myString);
        }
    }

    private static Object keepCast() {
        return "world";
    }
}

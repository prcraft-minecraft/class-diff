package io.github.prcraftmc.classdif.test.test2;



public record Hello(String a, int b) {
    public static void main(String[] args) {
        final Hello hi = new Hello("echo", 5);
        System.out.println(hi);
    }
}

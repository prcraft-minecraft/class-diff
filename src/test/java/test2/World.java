package test2;

import test1.Test;

public record World(String a, @Test.Inner float b) {
    public static void main(String[] args) {
        final World hi = new World("echo", 5f);
        System.out.println(hi);
    }
}

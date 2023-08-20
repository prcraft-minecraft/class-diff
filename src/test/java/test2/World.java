package test2;

import test1.Test;

public record World(String a, @Test.Inner float b) {
}

package test2;

import test1.Test;

public record World(@Test.Inner String a, @Test.Inner float b) {
}

package org.eclipse.tycho.debugtychosurefire.bundletest;

import static org.junit.Assert.assertEquals;

import org.eclipse.tycho.debugtychosurefire.bundle.HelloWorld;
import org.junit.Test;

public class HelloWorld2Test {
	 @Test
	    public void test3()
	    {
	        final HelloWorld hello = new HelloWorld();
	        assertEquals("Hello World", hello.test());
	    }
}

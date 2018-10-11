package test.nachos.threads;

import nachos.machine.*;
import nachos.threads.ThreadedKernel;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import static org.junit.Assert.*;

/**
 * KThread Tester.
 *
 * @author <Authors name>
 * @version 1.0
 * @since <pre>Oct 11, 2018</pre>
 */

public class KThreadTest {

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
        Machine.reset();
    }

    @Test
    public void testJoin() throws Exception {
        try {
            String configFile = this.getClass().getResource("nachos.conf").getFile().toString();
            Machine.main(new String[]{"-[]", configFile});
        } catch (Error e) {
            assertEquals("exitStatus0", e.getMessage());
        }
    }

}

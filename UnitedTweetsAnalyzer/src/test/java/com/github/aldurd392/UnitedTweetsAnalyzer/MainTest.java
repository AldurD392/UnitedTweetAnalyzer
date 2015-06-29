package com.github.aldurd392.UnitedTweetsAnalyzer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple Main.
 */
public class MainTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public MainTest(String testName)
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( MainTest.class );
    }

    /**
     * Test trimming and stemming.
     */
    public void testStemming() {
        assertEquals(Storage.stemLocation(""), null);
        assertEquals(Storage.stemLocation("Germany , Germany"), "germany germani");
        assertEquals(Storage.stemLocation("                 {  ITALIA   }"), "italia");
    }
}

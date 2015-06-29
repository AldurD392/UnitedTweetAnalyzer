package com.github.aldurd392.UnitedTweetsAnalyzer;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.geotools.geometry.jts.JTSFactoryFinder;

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
        assertEquals(Storage.stemLocation("Germany/Germany"), "germani germani");
        assertEquals(Storage.stemLocation("Germany , Germany"), "germani germani");
        assertEquals(Storage.stemLocation("                 {  ITALIA   }"), "italia");
    }

    /**
     * Test the envelope box containment.
     */
    public void testEnvelopeBox() {
        final Point point = JTSFactoryFinder.getGeometryFactory().createPoint(new Coordinate());

        point.getCoordinate().setOrdinate(0, -83.17);
        point.getCoordinate().setOrdinate(1, 30.87);
        point.geometryChanged();
        assertTrue(Geography.isInsideEnvelope(point));

        point.getCoordinate().setOrdinate(0, -118.13);
        point.getCoordinate().setOrdinate(1, 33.90);
        point.geometryChanged();
        assertTrue(Geography.isInsideEnvelope(point));

        point.getCoordinate().setOrdinate(0, 0);
        point.getCoordinate().setOrdinate(1, 0);
        point.geometryChanged();
        assertFalse(Geography.isInsideEnvelope(point));

        point.getCoordinate().setOrdinate(0, 11.05);
        point.getCoordinate().setOrdinate(1, 47.57);
        point.geometryChanged();
        assertFalse(Geography.isInsideEnvelope(point));
    }
}

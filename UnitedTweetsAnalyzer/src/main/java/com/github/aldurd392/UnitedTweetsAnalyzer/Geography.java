package com.github.aldurd392.UnitedTweetsAnalyzer;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import twitter4j.GeoLocation;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Handle the interactions with the Geography system.
 *
 * This class will load a shapefile and match/query points
 * against the polygons it contains.
 */
class Geography {
    private static final String DATASTORE_TYPE = "url";
    private static final String STATE_NAME = "NAME";

    /**
     * We indicate with UNKNOWN that the position
     * doesn't lie within any of the shapefile polygons.
     *
     * i.e. we're creating an UNKNOWN class.
     */
    public static final String UNKNOWN_COUNTRY = "UNKNOWN";

    /**
     * We'll store here the MultiPolygon extracted from the shapefile
     * in addition to their label (i.e. the country name).
     *
     * Queries costs O(n).
     * But in our case n is small (USA has ~50 states), so it's a good
     * trade-off between memory and time.
     */
    final private List<Map.Entry<String, MultiPolygon>> polygons;


    /**
     * Create the Geography object.
     * @param filePath the path to a shapefile object.
     * @throws IOException if can't open the shapefile path or any IO error occur.
     */
    public Geography(String filePath) throws IOException {
        final File file = new File(filePath);
        final Map<String, Serializable> map = new HashMap<>(1);
        map.put(DATASTORE_TYPE, file.toURI().toURL());

        SimpleFeatureIterator iterator = null;
        DataStore dataStore = null;
        try {
            dataStore = DataStoreFinder.getDataStore(map);
            final SimpleFeatureSource featureSource = dataStore.getFeatureSource(
                    dataStore.getTypeNames()[0]
            );

            final SimpleFeatureType schema = featureSource.getSchema();
            final String geometryAttributeName = schema.getGeometryDescriptor().getLocalName();

            final SimpleFeatureCollection features = featureSource.getFeatures();

            final ArrayList<Map.Entry<String, MultiPolygon>> polygons = new ArrayList<>(features.size());
            iterator = features.features();
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();

                MultiPolygon polygon = (MultiPolygon) feature.getAttribute(geometryAttributeName);
                String name = (String) feature.getAttribute(STATE_NAME);

                polygons.add(new AbstractMap.SimpleImmutableEntry<>(name, polygon));
            }

            this.polygons = Collections.unmodifiableList(polygons);
        } finally {
            if (iterator != null) {
                iterator.close();
            }

            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }

    /**
     * Query the polygons in our geography to find the country of the input coordinate.
     * @param coordinate the coordinate that needs to be queried.
     * @return the name of the coordinate state or UNKNOWN if not found.
     */
    public String query(Coordinate coordinate) {
        final Point point = JTSFactoryFinder.getGeometryFactory().createPoint(coordinate);

        /** Before trying to locate the country,
         * we make sure that the point lies within
         * the specified bounding box.
         */
        if (!Geography.isInsideEnvelope(point)) {
            return UNKNOWN_COUNTRY;
        }

        for (Map.Entry<String, MultiPolygon> entry : this.polygons) {
            if (entry.getValue().contains(point)) {
                return entry.getKey();
            }
        }

        return UNKNOWN_COUNTRY;
    }

    /**
     * Check if a point is inside the Constant envelopeBox.
     * @param p the point to be checked.
     * @return True if p is within the envelopeBox.
     */
    public static boolean isInsideEnvelope(Point p) {
        return Constants.envelopeBox.contains(p.getX(), p.getY());
    }

    /**
     * Return the midPoint between two Geolocations (i.e. GPS coordinates)
     * @param a first Geolocation.
     * @param b second Geolocation.
     * @return a new Geolocation located in the middle.
     */
    public static GeoLocation midPoint(GeoLocation a, GeoLocation b) {
        final double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());

        final double lat1 = Math.toRadians(a.getLatitude());
        final double lat2 = Math.toRadians(b.getLatitude());
        final double lon1 = Math.toRadians(a.getLongitude());

        final double Bx = Math.cos(lat2) * Math.cos(dLon);
        final double By = Math.cos(lat2) * Math.sin(dLon);
        final double lat3 = Math.atan2(Math.sin(lat1) + Math.sin(lat2),
                Math.sqrt((Math.cos(lat1) + Bx) * (Math.cos(lat1) + Bx)
                        + By * By));
        final double lon3 = lon1 + Math.atan2(By, Math.cos(lat1) + Bx);

        return new GeoLocation(Math.toDegrees(lat3), Math.toDegrees(lon3));
    }
}

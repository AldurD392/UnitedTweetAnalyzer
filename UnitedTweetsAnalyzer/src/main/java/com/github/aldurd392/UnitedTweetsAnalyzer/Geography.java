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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * UnitedTweetsAnalyzer - com.github.aldurd392.UnitedTweetsAnalyzer
 * Created by aldur on 02/06/15.
 */
public class Geography {

    private static final String TYPE = "url";
    private static final String STATE_NAME = "NAME";

    final private ArrayList<Map.Entry<String, MultiPolygon>> polygons;
    final private Point point = JTSFactoryFinder.getGeometryFactory().createPoint(new Coordinate());

    public Geography(String filePath) throws IOException {
        final File file = new File(filePath);
        final Map<String, Serializable> map = new HashMap<>();
        map.put(TYPE, file.toURI().toURL() );

        final DataStore dataStore = DataStoreFinder.getDataStore(map);
        final SimpleFeatureSource featureSource = dataStore.getFeatureSource(
                dataStore.getTypeNames()[0]
        );

        final SimpleFeatureType schema = featureSource.getSchema();
        final String geometryAttributeName = schema.getGeometryDescriptor().getLocalName();

        final SimpleFeatureCollection features = featureSource.getFeatures();
        this.polygons = new ArrayList<>(features.size());

        final SimpleFeatureIterator iterator = features.features();
        while (iterator.hasNext()) {
            SimpleFeature feature = iterator.next();

            MultiPolygon polygon = (MultiPolygon) feature.getAttribute(geometryAttributeName);
            String name = (String) feature.getAttribute(STATE_NAME);

            this.polygons.add(new AbstractMap.SimpleImmutableEntry<>(name, polygon));
        }
        iterator.close();

        dataStore.dispose();
    }

    public String query(Coordinate coordinate) {
        point.getCoordinate().setOrdinate(0, coordinate.getOrdinate(0));
        point.getCoordinate().setOrdinate(1, coordinate.getOrdinate(1));
        point.geometryChanged();

        for (Map.Entry<String, MultiPolygon> entry : this.polygons) {
            if (entry.getValue().contains(point)) {
                return entry.getKey();
            }
        }

        return null;
    }

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

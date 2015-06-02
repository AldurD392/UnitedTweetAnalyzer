package com.github.aldurd392.UnitedTweetsAnalyzer;

import com.vividsolutions.jts.geom.Coordinate;
import twitter4j.*;

/**
 * UnitedTweetsAnalyzer - com.github.aldurd392.UnitedTweetsAnalyzer
 * Created by aldur on 02/06/15.
 */
public class Streamer {

    private final Geography geography;

    public Streamer(Geography geography) {
        this.geography = geography;
    }

    public void startListening() {
        TwitterStream twitterStream = new TwitterStreamFactory().getInstance();

        StatusListener listener = new StatusListener() {
            @Override
            public void onStatus(Status status) {
                GeoLocation geoLocation;
                if ((geoLocation = status.getGeoLocation()) != null) {
                    System.out.println(geoLocation);

                    User user = status.getUser();
                    System.out.println("Username: " + user.getName());

                    String state = Streamer.this.geography.query(
                            new Coordinate(geoLocation.getLongitude(), geoLocation.getLatitude())
                    );
                    System.out.println(state);
                }
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) { }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) { }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) { }

            @Override
            public void onStallWarning(StallWarning warning) { }

            @Override
            public void onException(Exception ex) {
                ex.printStackTrace();
            }
        };

        twitterStream.addListener(listener);

        FilterQuery filterQuery = new FilterQuery();
        filterQuery.locations(Constants.boundingBox);
        twitterStream.filter(filterQuery);
    }
}

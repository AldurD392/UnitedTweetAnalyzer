package com.github.aldurd392.UnitedTweetsAnalyzer;

import twitter4j.*;

/**
 * UnitedTweetsAnalyzer - com.github.aldurd392.UnitedTweetsAnalyzer
 * Created by aldur on 02/06/15.
 */
class Streamer {
	private final Storage storage;

	public Streamer(Storage storage){
		this.storage = storage;
	}

    public void startListening(boolean locationBiased) {
        TwitterStream twitterStream = new TwitterStreamFactory().getInstance();

        StatusListener listener = new StatusListener() {
            @Override
            public void onStatus(Status status) {
                storage.insertTweet(status);
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

        if (locationBiased) {
            FilterQuery filterQuery = new FilterQuery();
            filterQuery.locations(Constants.boundingBox);
            twitterStream.filter(filterQuery);
        } else {
            twitterStream.sample();
        }
    }
}

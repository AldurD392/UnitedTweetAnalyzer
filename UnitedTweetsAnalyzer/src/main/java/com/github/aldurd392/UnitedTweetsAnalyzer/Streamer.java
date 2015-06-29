package com.github.aldurd392.UnitedTweetsAnalyzer;

import org.apache.logging.log4j.LogManager;
import twitter4j.*;

/**
 * Connect to the Twitter stream and store the results.
 */
class Streamer {
    private final static org.apache.logging.log4j.Logger logger = LogManager.getLogger(Streamer.class.getSimpleName());

	private final Storage storage;

    /**
     * Build the streamer and prepare the storage.
     * @param storage the storage in which stream Statues will be stored.
     */
	public Streamer(Storage storage){
        assert (storage != null);
		this.storage = storage;
	}

    /**
     * Start listening to the stream and storing into the storage.
     * @param locationBiased parameter indicating if the stream
     *                       has to be filtered according to the USA
     *                       bounding box or has to be a random 1% sample.
     */
    public void startListening(boolean locationBiased) {
        TwitterStream twitterStream = new TwitterStreamFactory().getInstance();

        StatusListener listener = new StatusListener() {
            @Override
            public void onStatus(Status status) {
                storage.insertTweet(status);
            }

            @Override
            public void onException(Exception e) {
                logger.error("Error on stream", e);
            }

            @Override
            public void onStallWarning(StallWarning warning) {
                logger.warn("Got stall warning: {}", warning);
            }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
                logger.warn("Got track limitation notice {}", numberOfLimitedStatuses);
            }

            /*
             * According to Twitter APIs we'd need to implement those methods too.
             * This is an academic project, so we'd be fine here.
             */
            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) { }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) { }
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

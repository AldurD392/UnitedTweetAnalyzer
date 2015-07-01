package com.github.aldurd392.UnitedTweetsAnalyzer;

import org.apache.logging.log4j.LogManager;
import twitter4j.*;

/**
 * Connect to the Twitter stream and store the results.
 */
class Streamer {

    /**
     * Different bias for the stream.
     */
    public enum Bias {
        /**
         * Sample from the whole stream.
         */
        all,
        /**
         * Require tweets to be within the bounding box,
         * see {@link Constants#boundingBox}.
         */
        geo,
        /**
         * Require tweets to have an attached geo-location.
         */
        all_geo,
    }

    private final static org.apache.logging.log4j.Logger logger = LogManager.getLogger(Streamer.class.getSimpleName());

	private final Storage storage;
    private TwitterStream twitterStream = null;

    /**
     * Build the streamer and prepare the storage.
     * @param storage the storage in which stream Statues will be stored.
     */
	public Streamer(Storage storage) {
        assert (storage != null);
		this.storage = storage;
	}

    /**
     * Start listening to the stream and storing into the storage.
     * @param bias parameter indicating how the stream should be filtered.
     */
    public void startListening(Bias bias) {
        this.twitterStream = new TwitterStreamFactory().getInstance();

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

        final FilterQuery filterQuery = new FilterQuery();
        switch (bias) {
            case geo:
                filterQuery.locations(Constants.boundingBox);
                twitterStream.filter(filterQuery);
                break;
            case all_geo:
                filterQuery.locations(Constants.worldWideBox);
                twitterStream.filter(filterQuery);
                break;
            case all:
                twitterStream.sample();
                break;
        }
    }

    public void stopListening() {
        if (this.twitterStream != null) {
            this.twitterStream.clearListeners();
            this.twitterStream.cleanUp();
        }
    }
}

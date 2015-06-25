package com.github.aldurd392.UnitedTweetsAnalyzer;

import com.vividsolutions.jts.geom.Coordinate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import twitter4j.GeoLocation;
import twitter4j.Status;
import twitter4j.User;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class Storage {
    private final static String JDBC_PREFIX = "jdbc:sqlite:";
    private final static String JDBC_DRIVER = "org.sqlite.JDBC";

    public final static String ID = "ID";

    public final static String USERNAME = "USERNAME";
    public final static String LANG = "LANG";
    public final static String LOCATION = "LOCATION";
    public final static String UTC_OFFSET = "UTC_OFFSET";
    public final static String TIMEZONE = "TIMEZONE";

    public final static String LAT = "LAT";
    public final static String LON = "LON";
    public final static String USER_ID = "USER_ID";

    public final static String TABLE_USER = "USER";
    public final static String TABLE_TWEET = "TWEET";

    public final static String COUNTRY = "COUNTRY";  // This will also be the classifier class

    private final static Logger logger = LogManager.getLogger(Storage.class.getSimpleName());

    private final Geography geography;
    private Connection c = null;

    public Storage(Geography geography, String database_path) {
        this.geography = geography;
        this.connect(database_path);
    }

    private void connect(String dp_path) {
        boolean exist = this.checkDataBase(dp_path);

        try {
            /*
             * Class.forName() dynamically loads the class
             * in order to execute static blocks only once.
             */
            Class.forName(JDBC_DRIVER);
            this.c = DriverManager.getConnection(JDBC_PREFIX + dp_path);

            logger.debug("Database successfully opened.");
            if (!exist) {
                this.initDatabase();
            }
        } catch (SQLException | ClassNotFoundException e) {
            logger.error("Error while creating tables.");
            logger.debug(e);
            System.exit(1);
        }
    }

    private boolean checkDataBase(String db_path) {
        return new File(db_path).exists();
    }

    private void initDatabase() throws SQLException {
        try (Statement stmt = this.c.createStatement()) {
            String userTable = "CREATE TABLE " + TABLE_USER +
                    String.format(" (%s UNSIGNED BIG INT PRIMARY KEY NOT NULL," +
                                    " %s TEXT NOT NULL," +
                                    " %s VARCHAR(10)," +
                                    " %s VARCHAR(100)," +
                                    " %s INT," +
                                    " %s VARCHAR(50))",
                            ID, USERNAME, LANG, LOCATION, UTC_OFFSET, TIMEZONE);
            stmt.executeUpdate(userTable);

            String tweetTable = "CREATE TABLE " + TABLE_TWEET +
                    String.format(" (%s UNSIGNED BIG INT PRIMARY KEY NOT NULL," +
                                    " %s FLOAT NOT NULL," +
                                    " %s FLOAT NOT NULL," +
                                    " %s VARCHAR(50)," +
                                    " %s UNSIGNED BIG INT," +
                                    " FOREIGN KEY(%s) REFERENCES %s(%s))",
                            ID, LAT, LON, COUNTRY, USER_ID, USER_ID, TABLE_USER, ID);
            stmt.executeUpdate(tweetTable);

            logger.debug("Tables successfully created.");
        }
    }

    private void insertUser(User user) throws SQLException {
        if (user == null) {
            logger.error("Trying to add NULL user to the DB.");
            return;
        }

        String select = String.format("SELECT %s FROM %s WHERE %s = ?;",
                ID, TABLE_USER, ID);
        try (PreparedStatement stmt = this.c.prepareStatement(select)) {
            stmt.setLong(1, user.getId());

            if (stmt.executeQuery().next()) {
                /* The user already exists in our DB */
                logger.debug("User {} - {} already exists in DB.", user.getId(), user.getName());
                return;
            }
        } catch (SQLException e) {
            logger.error("Error while selecting user {}", user.getId());
            logger.debug(e);

            throw e;
        }

        String insert = "INSERT INTO " + TABLE_USER +
                String.format(" (%s, %s, %s, %s, %s, %s) ",
                        ID, USERNAME, LANG, LOCATION, UTC_OFFSET, TIMEZONE
                ) + "VALUES (?, ?, ?, ?, ?, ?);";
        try (PreparedStatement stmt = this.c.prepareStatement(insert)) {
            stmt.setLong(1, user.getId());
            stmt.setString(2, user.getName());
            stmt.setString(3, user.getLang());
            stmt.setString(4, user.getLocation());
            stmt.setInt(5, user.getUtcOffset());
            stmt.setString(6, user.getTimeZone());

            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error while inserting user {} {}", user.getId(), user.getName());
            logger.debug(e);

            throw e;
        }
    }

    public void insertTweet(Status tweet) {
        try {
            this.insertUser(tweet.getUser());
        } catch (SQLException e) {
            logger.warn("Skipping tweet {}, error while inserting user {}.",
                    tweet.getId(), tweet.getUser().getId()
            );
        }

        assert tweet.getGeoLocation() != null || tweet.getPlace() != null;

        GeoLocation geoLocation = tweet.getGeoLocation();
        if (geoLocation == null) {
            final int rows = tweet.getPlace().getBoundingBoxCoordinates().length;
            final int columns = tweet.getPlace().getBoundingBoxCoordinates()[rows - 1].length;

            final GeoLocation first = tweet.getPlace().getBoundingBoxCoordinates()[0][0];
            final GeoLocation last = tweet.getPlace().getBoundingBoxCoordinates()[rows - 1][columns - 1];

            geoLocation = Geography.midPoint(first, last);
        }

        assert geoLocation != null;

        String country = this.geography.query(
                new Coordinate(geoLocation.getLongitude(), geoLocation.getLatitude())
        );

        if (country == null) {
            logger.warn("Skipping tweet whose country is unknown: {} - ({}, {})",
                    tweet.getId(),
                    geoLocation.getLatitude(), geoLocation.getLongitude());
            return;
        }

        logger.debug("Tweet {}: {}", tweet.getId(), country);  // DEBUG

        String select = String.format("SELECT %s FROM %s WHERE %s = ?;",
                ID, TABLE_TWEET, ID);
        try (PreparedStatement stmt = this.c.prepareStatement(select)) {
            stmt.setLong(1, tweet.getId());

            if (stmt.executeQuery().next()) {
                    /* The tweet already exists in our DB */
                logger.debug("Tweet {} already exists in DB.", tweet.getId());
                return;
            }
        } catch (SQLException e) {
            logger.error("Error while selecting tweet {}", tweet.getId());
            logger.debug(e);

            return;
        }


        String insert = "INSERT INTO " + TABLE_TWEET +
                String.format(
                        " (%s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?);",
                        ID, LAT, LON, COUNTRY, USER_ID);

        try (PreparedStatement stmt = this.c.prepareStatement(insert)) {
            stmt.setLong(1, tweet.getId());
            stmt.setDouble(2, geoLocation.getLatitude());
            stmt.setDouble(3, geoLocation.getLongitude());
            stmt.setString(4, country);
            stmt.setLong(5, tweet.getUser().getId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error while inserting tweet {}", tweet.getId());
            logger.debug(e);
        }
    }

    public void close() throws SQLException {
        this.c.close();
    }
}

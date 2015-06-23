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
import java.sql.SQLException;
import java.sql.Statement;

public class Storage {
    private final static String JDBC_PREFIX = "jdbc:sqlite:";
    private final static String JDBC_DRIVER = "org.sqlite.JDBC";

    private final static String ID = "ID";

    private final static String USERNAME = "USERNAME";
    private final static String LANG = "LANG";
    private final static String LOCATION = "LOCATION";
    private final static String UTC_OFFSET = "UTC_OFFSET";
    private final static String TIMEZONE = "TIMEZONE";

    private final static String LAT = "LAT";
    private final static String LON = "LON";
    private final static String COUNTRY = "COUNTRY";
    private final static String USER_ID = "USER_ID";

    private final static String TABLE_USER = "USER";
    private final static String TABLE_TWEET = "TWEET";

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
             * Class.forName() load dynamically the class
             * to execute static blocks only once.
             */
            Class.forName(JDBC_DRIVER);
            this.c = DriverManager.getConnection(JDBC_PREFIX + dp_path);

            logger.debug("Database successfully opeened.");
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
        Statement stmt = this.c.createStatement();

        logger.debug("Creating tables...");
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

        stmt.close();
    }

    private void insertUser(User user) throws SQLException {
        if (user == null) {
            logger.error("Trying to add NULL user to the DB.");
            return;
        }

        String select = String.format("SELECT %s FROM %s WHERE %s = %d;",
                ID, TABLE_USER, ID, user.getId());
        try (Statement stmt = this.c.createStatement()) {
            if (stmt.executeQuery(select).next()) {
            /* The user already exists in our DB */
                logger.debug("User {} - {} already exists in DB.", user.getId(), user.getName());
                return;
            }
        } catch (SQLException e) {
            logger.error("Error while selecting user {}", user.getId());
            logger.debug("{}", select);
            logger.debug(e);

            throw e;
        }

        String insert = "INSERT INTO " + TABLE_USER +
                String.format(" (%s, %s, %s, %s, %s, %s) ",
                        ID, USERNAME, LANG, LOCATION, UTC_OFFSET, TIMEZONE)
                +
                String.format("VALUES (%d, '%s', %s, %s, %d, %s);",
                        user.getId(),
                        user.getName().replace("'", "\'").replace('"', '\"'),
                        user.getLang() == null ? null : String.format("'%s'", user.getLang()),
                        user.getLocation() == null ? null : String.format("'%s'", user.getLocation()),
                        user.getUtcOffset(),
                        user.getTimeZone() == null ? null : String.format("'%s'", user.getTimeZone()));
        try (Statement stmt = this.c.createStatement()) {
            stmt.executeUpdate(insert);
        } catch (SQLException e) {
            logger.error("Error while inserting user {} {}", user.getId(), user.getName());
            logger.debug("{}", insert);
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

        GeoLocation geoLocation;
        if ((geoLocation = tweet.getGeoLocation()) != null) {
            String country = this.geography.query(
                    new Coordinate(geoLocation.getLongitude(), geoLocation.getLatitude())
            );

            if (country != null) {
                String select = String.format("SELECT %s FROM %s WHERE %s = %d;",
                        ID, TABLE_TWEET, ID, tweet.getId());
                try (Statement stmt = this.c.createStatement()) {
                    if (stmt.executeQuery(select).next()) {
                        /* The tweet already exists in our DB */
                        logger.debug("Tweet {} already exists in DB.", tweet.getId());
                        logger.debug("{}", select);
                        return;
                    }
                } catch (SQLException e) {
                    logger.error("Error while selecting tweet {}", tweet.getId());
                    logger.debug(e);

                    return;
                }


                String insert = "INSERT INTO " + TABLE_TWEET +
                        String.format(
                                " (%s, %s, %s, %s, %s) ",
                                ID, LAT, LON, COUNTRY, USER_ID)
                        +
                        String.format(
                                "VALUES (%d, %f, %f, '%s', %d);",
                                tweet.getId(), tweet.getGeoLocation().getLatitude(),
                                tweet.getGeoLocation().getLongitude(), country,
                                tweet.getUser().getId());
                try (Statement stmt = this.c.createStatement()) {
                    stmt.executeUpdate(insert);
                } catch (SQLException e) {
                    logger.error("Error while inserting tweet {}", tweet.getId());
                    logger.debug("{}", insert);
                    logger.debug(e);
                }
            }
        }
    }

    public void close() throws SQLException {
        this.c.close();
    }
}

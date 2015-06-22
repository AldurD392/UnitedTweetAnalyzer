package com.github.aldurd392.UnitedTweetsAnalyzer;

import com.vividsolutions.jts.geom.Coordinate;
import twitter4j.GeoLocation;
import twitter4j.Status;
import twitter4j.User;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


public class Storage {
    private final static String TAG = Storage.class.getSimpleName() + ": ";

    private final static String JDBC_PREFIX = "jdbc:sqlite:";

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
            Class.forName("org.sqlite.JDBC");
            this.c = DriverManager.getConnection(JDBC_PREFIX + dp_path);

            System.out.println(TAG + "Database successfully opened.");
            if (!exist) {
                this.initDatabase();
            }
        } catch (Exception e) {
            System.err.println(TAG + e.getMessage());
            System.exit(1);
        }
    }

    private boolean checkDataBase(String db_path) {
        return new File(db_path).exists();
    }

    private void initDatabase() throws SQLException {
        Statement stmt = this.c.createStatement();

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
        System.out.println(TAG + "creating tables...");
    }

    private void insertUser(User user) throws SQLException {
        if (user == null) {
            // TODO: log error.
            return;
        }

        // TODO: check for existence.
        Statement stmt = this.c.createStatement();
        String insert = "INSERT INTO " + TABLE_USER +
                String.format(" (%s, %s, %s, %s, %s, %s) ",
                        ID, USERNAME, LANG, LOCATION, UTC_OFFSET, TIMEZONE)
                +
                String.format("VALUES (%d, '%s', %s, %s, %d, %s);",
                        user.getId(),
                        user.getName(),
                        user.getLang() == null ? null : String.format("'%s'", user.getLang()), 
                        user.getLocation() == null ? null : String.format("'%s'", user.getLocation()),
                        user.getUtcOffset(),
                        user.getTimeZone() == null ? null : String.format("'%s'", user.getTimeZone()));
        stmt.executeUpdate(insert);
        stmt.close();
    }

    public void insertTweet(Status tweet) throws SQLException {
        this.insertUser(tweet.getUser());

        GeoLocation geoLocation;
        if ((geoLocation = tweet.getGeoLocation()) != null) {
            String country = this.geography.query(
                    new Coordinate(geoLocation.getLongitude(), geoLocation.getLatitude())
            );

            if (country != null) {
                final Statement stmt = this.c.createStatement();

                // TODO: check for existence.
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

                stmt.executeUpdate(insert);
                stmt.close();
            }
        }
    }

    public void close() throws SQLException {
        this.c.close();
    }
}

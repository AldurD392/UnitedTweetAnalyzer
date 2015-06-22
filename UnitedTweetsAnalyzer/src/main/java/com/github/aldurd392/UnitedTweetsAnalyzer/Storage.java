package com.github.aldurd392.UnitedTweetsAnalyzer;

import java.sql.Statement;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.vividsolutions.jts.geom.Coordinate;

import twitter4j.GeoLocation;
import twitter4j.Status;
import twitter4j.User;


public class Storage {
	
	private Connection c = null;
    private final Geography geography;
	
	public Storage(Geography geography){
        this.geography = geography;
	}
	
	public void connect(String dbName){
		boolean exist = this.checkDataBase(dbName);
		
		try {
			Class.forName("org.sqlite.JDBC");
			this.c = DriverManager.getConnection("jdbc:sqlite:" + dbName);
			
			System.out.println("Opened database successfully");
			
			if (!exist) {
				this.initDatabase();
				System.out.println("Creazione tabelle");
			}
			
			
		} catch ( Exception e ) {
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
	    }
	}
	
	private boolean checkDataBase(String dbName) {
		File f = new File(dbName);
		if(f.exists()){
			return true;
		} else {
			return false;
		}
	}
	
	private void initDatabase() throws SQLException{
		Statement stmt = this.c.createStatement();
		
		String userTable = "CREATE TABLE USER " +
                   "(ID UNSIGNED BIG INT PRIMARY KEY NOT NULL," +
                   " USERNAME TEXT NOT NULL," +
                   " LANG VARCHAR(10)," + 
                   " LOCATION VARCHAR(100)," +
                   " UTC_OFFSET INT," + 
                   " TIMEZONE VARCHAR(50))";
		
		stmt.executeUpdate(userTable);		
		
		String tweetTable = "CREATE TABLE TWEET " +
                   "(ID UNSIGNED BIG INT PRIMARY KEY NOT NULL," +
                   " LAT FLOAT NOT NULL," + 
                   " LON FLOAT NOT NULL," +
                   " COUNTRY VARCHAR(50)," +
                   " USER_ID UNSIGNED BIG INT,"+ 
                   " FOREIGN KEY(USER_ID) REFERENCES USER(ID))";						
		
		stmt.executeUpdate(tweetTable);
		stmt.close();

		System.out.println("Creazione tabelle");
	}
	
	public void insertUser(User user) throws SQLException{
		
		Statement stmt = this.c.createStatement();
		
		String insert = String.format(
						"INSERT INTO USER " +
						"(ID, USERNAME, LANG, LOCATION, UTC_OFFSET, TIMEZONE) " +
						"VALUES (%l, %s, %s, %s, %d, %s);", 
						user.getId(), user.getName(), user.getLang(), 
						user.getLocation(), user.getUtcOffset(), 
						user.getTimeZone());
		
		stmt.executeUpdate(insert);
		stmt.close();
	}
	
	public void insertTweet(Status tweet) throws SQLException{
		
		Statement stmt = this.c.createStatement();
        GeoLocation geoLocation;
        String country = null;
		
		if ((geoLocation = tweet.getGeoLocation()) != null) {
            country = this.geography.query(
                    new Coordinate(geoLocation.getLongitude(), geoLocation.getLatitude())
            );

		}
		
		String insert = String.format(
						"INSERT INTO TWEET " +
						"(ID, LAT, LON, COUNTRY, USER_ID) " +
						"VALUES (%l, %f, %f, %s, %l);", 
						tweet.getId(), tweet.getGeoLocation().getLatitude(), 
						tweet.getGeoLocation().getLongitude(), country, 
						tweet.getUser().getId());
		
		stmt.executeUpdate(insert);
		stmt.close();
	}
	
	public void close() throws SQLException{
		this.c.close();
	}
}

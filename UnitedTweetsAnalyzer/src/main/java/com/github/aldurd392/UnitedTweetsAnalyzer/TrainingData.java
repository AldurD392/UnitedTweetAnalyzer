package com.github.aldurd392.UnitedTweetsAnalyzer;

import weka.core.Instances;
import weka.experiment.InstanceQuery;

public class TrainingData {
	
	private Instances data = null;
	
	private final String stringQuery = "SELECT USERNAME "
									+ "FROM USER, TWEET "
									+ "WHERE USER.ID = TWEET.USER_ID;";
	
	public TrainingData(){
		
	}

	public void loadTrainingData() throws Exception{
		InstanceQuery query = new InstanceQuery();
		query.setUsername("nobody");
		query.setPassword("");
		query.setQuery(stringQuery);
		this.data = query.retrieveInstances();
	}
	
	public Instances getData(){
		return data;
	}
}


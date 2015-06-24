package com.github.aldurd392.UnitedTweetsAnalyzer;

import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.core.Instance;
import weka.core.Instances;

public class NaiveBayes {
	
	private TrainingData train = null;
	
	public NaiveBayes(TrainingData train) {
		this.train = train;
	}
	
	public void classify() throws Exception {
		NaiveBayesUpdateable nb = new NaiveBayesUpdateable();
		Instances data = this.train.getData();

		data.setClassIndex(data.numAttributes() -1);
		nb.buildClassifier(data);
		
		Evaluation eval = new Evaluation(data);
		eval.evaluateModel(nb, data);
		System.out.println(eval.toSummaryString("\nResults\n======\n", false));

	}
}

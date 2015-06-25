package com.github.aldurd392.UnitedTweetsAnalyzer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.experiment.InstanceQuery;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * UnitedTweetsAnalyzer - com.github.aldurd392.UnitedTweetsAnalyzer
 * Created by aldur on 25/06/15.
 */
public class Learner {
    private final static Logger logger = LogManager.getLogger(Learner.class.getSimpleName());

    public final static Map<String, Class> classifiers = Collections.unmodifiableMap(
            new HashMap<String, Class>() {
                {
                    put("all", null);
                    put("nbayes", NaiveBayesUpdateable.class);
                    put("dtree", J48.class);
                }
            }
    );

    private final static String stringQuery = String.format(
            "SELECT %s.*, %s.%s " +
                    "FROM %s, %s " +
                    "WHERE %s.%s = %s.%s",
            Storage.TABLE_USER, Storage.TABLE_TWEET, Storage.COUNTRY,
            Storage.TABLE_USER, Storage.TABLE_TWEET,
            Storage.TABLE_USER, Storage.ID, Storage.TABLE_TWEET, Storage.USER_ID);

    private Instances training_data = null;
    private AbstractClassifier classifier = null;

    public Learner(String classifier_name) throws Exception {
        super();

        this.loadData();
        this.classifierFactory(classifier_name);
    }

    public void loadData() throws Exception {
        InstanceQuery query = null;

        try {
            query = new InstanceQuery();
            query.setUsername("nobody");
            query.setPassword("");
            query.setQuery(stringQuery);

            this.training_data = query.retrieveInstances();
            this.training_data.setClass(this.training_data.attribute(Storage.COUNTRY));
            this.training_data.randomize(new Random(0));
        } catch (Exception e) {
            logger.fatal("Error while executing DB query.");
            logger.debug(e);

            throw e;
        } finally {
            if (query != null) {
                query.close();
            }
        }
    }

    private void classifierFactory(String classifier_name) throws Exception {
        Class<?> classifier_class = Learner.classifiers.get(classifier_name);

        if (classifier_class == null) {
            final String error = "Unknown classifier name " + classifier_name;
            logger.error(error);
            throw new Exception(error);
        }

        try {
            Constructor<?> constructor = classifier_class.getConstructor();
            Object object = constructor.newInstance();

            assert (object instanceof AbstractClassifier);
            this.classifier = (AbstractClassifier) object;
        } catch (NoSuchMethodException |
                InvocationTargetException |
                InstantiationException |
                IllegalAccessException e) {
            logger.error("Error while instantiating classifier {}", classifier_class.getSimpleName());
            logger.debug(e);
            throw e;
        }

        logger.debug("Classifier {} correctly created.", this.classifier.getClass().getSimpleName());
    }

    public void buildClassifier() throws Exception {
        logger.info("Building classifier {}...", this.classifier.getClass().getSimpleName());

        try {
            this.classifier.buildClassifier(this.training_data);
        } catch (Exception e) {
            logger.error("Error while building classifier");
            throw e;
        }
    }

    private Map.Entry<Instances, Instances> splitTrainingTestData(float percentage_split) {
        assert (percentage_split > 0 && percentage_split < 1);

        final int trainingSize = (int) Math.round(this.training_data.numInstances() * (1.0 - percentage_split));
        final int testingSize = this.training_data.numInstances() - trainingSize;

        final Instances train = new Instances(training_data, 0, trainingSize);
        final Instances test = new Instances(training_data, trainingSize, testingSize);

        return new AbstractMap.SimpleEntry<>(train, test);
    }

    public void evaluateClassifier(float evaluation_rate) {
        logger.info("Evaluating built classifier {} and evaluation rate {}...",
                this.classifier.getClass().getSimpleName(), evaluation_rate);

        try {
            Evaluation eval;

            if (evaluation_rate < 1) {
                Map.Entry<Instances, Instances> data = splitTrainingTestData(evaluation_rate);
                eval = new Evaluation(data.getKey());
                eval.evaluateModel(this.classifier, data.getValue());
            } else {
                eval = new Evaluation(this.training_data);
                int rounded_evaluation_rate = Math.round(evaluation_rate);
                eval.crossValidateModel(
                        this.classifier,
                        this.training_data,
                        rounded_evaluation_rate,
                        new Random()
                );
            }

            System.out.println(eval.toSummaryString("\nResults\n======\n", false));
        } catch (Exception e) {
            logger.error("Error while evaluating the classifier");
            logger.debug(e);
        }
    }
}

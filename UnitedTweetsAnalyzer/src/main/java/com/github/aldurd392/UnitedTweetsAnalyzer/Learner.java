package com.github.aldurd392.UnitedTweetsAnalyzer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.classifiers.functions.LibSVM;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.experiment.InstanceQuery;
import weka.filters.unsupervised.attribute.Remove;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * UnitedTweetsAnalyzer - com.github.aldurd392.UnitedTweetsAnalyzer
 * Created by aldur on 25/06/15.
 */
class Learner {
    private final static Logger logger = LogManager.getLogger(Learner.class.getSimpleName());

    public final static Map<String, Class<? extends AbstractClassifier>> classifiers;

    static {
        HashMap<String, Class<? extends AbstractClassifier>> map = new HashMap<>();
        map.put("nbayes", NaiveBayesUpdateable.class);
        map.put("dtree", J48.class);
        map.put("libsvm", LibSVM.class);
//        map.put("smo", SMO.class);
//        map.put("random_forest", RandomForest.class);
        classifiers = Collections.unmodifiableMap(map);
    }

    private final static String trainingQuery = String.format(
            "SELECT %s.%s, %s.%s, %s.%s, %s.%s, %s.%s, %s.%s " +
                    "FROM %s, %s " +
                    "WHERE %s.%s = %s.%s",
            Storage.TABLE_USER, Storage.ID,
            Storage.TABLE_USER, Storage.LANG,
            Storage.TABLE_USER, Storage.LOCATION,
            Storage.TABLE_USER, Storage.UTC_OFFSET,
            Storage.TABLE_USER, Storage.TIMEZONE,
            Storage.TABLE_TWEET, Storage.COUNTRY,
            Storage.TABLE_USER, Storage.TABLE_TWEET,
            Storage.TABLE_USER, Storage.ID, Storage.TABLE_TWEET, Storage.USER_ID);

    private final static String classificationQuery = String.format(
            "SELECT %s.%s ,%s.%s, %s.%s, %s.%s, %s.%s, NULL as %s " +
                    "FROM %s " +
                    "WHERE %s not in " +
                    "(" +
                    "SELECT %s.%s " +
                    "FROM %s, %s " +
                    "WHERE %s.%s = %s.%s" +
                    ")",
            Storage.TABLE_USER, Storage.ID,
            Storage.TABLE_USER, Storage.LANG,
            Storage.TABLE_USER, Storage.LOCATION,
            Storage.TABLE_USER, Storage.UTC_OFFSET,
            Storage.TABLE_USER, Storage.TIMEZONE,
            Storage.COUNTRY,
            Storage.TABLE_USER,
            Storage.ID,
            Storage.TABLE_USER, Storage.ID,
            Storage.TABLE_USER, Storage.TABLE_TWEET,
            Storage.TABLE_USER, Storage.ID, Storage.TABLE_TWEET, Storage.USER_ID);

    private Instances training_data = null;
    private Instances classification_data = null;
    private FilteredClassifier classifier = null;

    public Learner(String classifier_name) throws Exception {
        super();

        this.loadData(true);
        this.classifierFactory(classifier_name);
    }

    public Instances getTrainingData() {
        return this.training_data;
    }

    public FilteredClassifier getClassifier() {
        return this.classifier;
    }

    private void loadData(boolean isTraining) throws Exception {
        InstanceQuery query = null;

        try {
            query = new InstanceQuery();
            query.setUsername("nobody");
            query.setPassword("");

            if (isTraining) {
                query.setQuery(trainingQuery);

                this.training_data = query.retrieveInstances();
                this.training_data.setClass(this.training_data.attribute(Storage.COUNTRY));
                this.training_data.randomize(new Random(0));
            } else {
                query.setQuery(classificationQuery);
                this.classification_data = query.retrieveInstances();
                this.classification_data.setClass(this.classification_data.attribute(Storage.COUNTRY));
                this.classification_data.randomize(new Random(0));
            }
            
        } catch (Exception e) {
            logger.error("Error while executing DB query", e);
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

            Remove rm = new Remove();
    		
    			Attribute idAttr = this.training_data.attribute(
            		this.training_data.attribute(Storage.ID).index()
            	);
    			rm.setAttributeIndices("" + (idAttr.index() + 1));
    		
    			this.classifier = new FilteredClassifier();
    			this.classifier.setFilter(rm);
    			this.classifier.setClassifier((AbstractClassifier) object);

        } catch (NoSuchMethodException |
                InvocationTargetException |
                InstantiationException |
                IllegalAccessException e) {
            logger.error("Error while instantiating classifier {}", classifier_class.getSimpleName());
            logger.debug(e);
            throw e;
        }

        logger.debug("Classifier {} correctly created.", this.classifier.getClassifier().getClass().getSimpleName());
    }

    private Map.Entry<Instances, Instances> splitTrainingTestData(float percentage_split) {
        assert (percentage_split > 0 && percentage_split < 1);

        final int trainingSize = (int) Math.round(this.training_data.numInstances() * (1.0 - percentage_split));
        final int testingSize = this.training_data.numInstances() - trainingSize;

        final Instances train = new Instances(training_data, 0, trainingSize);
        final Instances test = new Instances(training_data, trainingSize, testingSize);

        return new AbstractMap.SimpleEntry<>(train, test);
    }

    public void buildAndClassify() {
        try {
        	
            logger.info("Building classifier {}...",
            		this.classifier.getClassifier().getClass().getSimpleName());
            this.classifier.buildClassifier(this.training_data);

            this.loadData(false);

            for (Instance i : this.classification_data) {
            		
                double classification = this.classifier.classifyInstance(i);

//                logger.debug(i.toString());
                logger.debug("Classification: id: {}, class: {}",
                			(int) i.value(this.training_data.attribute(Storage.ID).index()),
                        this.training_data.classAttribute().value((int) classification)
                );
            }
        } catch (Exception e) {
            logger.error("Error while classifying new instances.", e);
        }
    }

    public Evaluation buildAndEvaluate(float evaluation_rate) {
        Evaluation eval = null;

        try {
            if (evaluation_rate < 1) {
                logger.info("Building and evaluating classifier {} with testing percentage {}...",
                		this.classifier.getClassifier().getClass().getSimpleName(), evaluation_rate);

                Map.Entry<Instances, Instances> data = splitTrainingTestData(evaluation_rate);

                this.classifier.buildClassifier(data.getKey());

                eval = new Evaluation(data.getKey());
                eval.evaluateModel(this.classifier, data.getValue());
            } else {
                eval = new Evaluation(this.training_data);
                int rounded_evaluation_rate = Math.round(evaluation_rate);

                logger.info("Building and evaluating classifier {} with {}-fold validation...",
                        this.classifier.getClassifier().getClass().getSimpleName(), 
                        rounded_evaluation_rate);

                eval.crossValidateModel(
                        this.classifier,
                        this.training_data,
                        rounded_evaluation_rate,
                        new Random()
                );
            }
        } catch (Exception e) {
            logger.error("Error while evaluating the classifier");
            logger.debug(e);
        }

        return eval;
    }
}

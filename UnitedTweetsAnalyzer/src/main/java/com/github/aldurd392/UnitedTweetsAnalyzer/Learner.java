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

    /**
     * This map will let you add other classifiers to this class.
     * You can specify all those classifiers that inherit from AbstractClassifier
     *
     * @see AbstractClassifier
     */
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

    /**
     * Load from the DB all the labeled instances.
     * i.e. users who have at least a tweet labeled
     * with an associated country.
     */
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

    /**
     * Load from the DB all the unlabeled instances.
     * Those instances will be used for the unsupervised
     * machine learning task.
     * <p/>
     * Please note that the format retrieved by this query
     * must be equal to the one retrieved by trainingQuery.
     */
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

    /**
     * Build a new learner
     *
     * @param classifier_name the name of the classifier used by the learner.
     * @throws Exception on error.
     */
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

    /**
     * Load data from the DB and store them in instance variables.
     * Note that always randomize the order of the retrieved instances.
     *
     * @param isTraining if set means that we have to load training instances.
     *                   Otherwise, we're classifying new instances.
     * @throws Exception on error.
     */
    private void loadData(boolean isTraining) throws Exception {
        InstanceQuery query = null;

        try {
            query = new InstanceQuery();
            query.setUsername("nobody");
            query.setPassword("");

            Instances instances;
            if (isTraining) {
                query.setQuery(trainingQuery);
                this.training_data = query.retrieveInstances();
                instances = this.training_data;
            } else {
                query.setQuery(classificationQuery);
                this.classification_data = query.retrieveInstances();
                instances = this.classification_data;
            }

            instances.setClass(instances.attribute(Storage.COUNTRY));
            instances.randomize(new Random());
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
        Class<? extends AbstractClassifier> classifier_class = Learner.classifiers.get(classifier_name);

        if (classifier_class == null) {
            final String error = "Unknown classifier name " + classifier_name;
            logger.error(error);
            throw new Exception(error);
        }

        try {
            Constructor<? extends AbstractClassifier> constructor = classifier_class.getConstructor();
            AbstractClassifier abstractClassifier = constructor.newInstance();

            Remove remove = new Remove();
            Attribute idAttr = this.training_data.attribute(
                    this.training_data.attribute(Storage.ID).index()
            );
            remove.setAttributeIndices(
                    String.format("%d", idAttr.index() + 1)
            );

            this.classifier = new FilteredClassifier();
            this.classifier.setFilter(remove);
            this.classifier.setClassifier(abstractClassifier);
        } catch (NoSuchMethodException |
                InvocationTargetException |
                InstantiationException |
                IllegalAccessException e) {
            logger.error("Error while instantiating classifier {}", classifier_class.getSimpleName(), e);
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

    /**
     * Classify unseen instances.
     * It builds the classifier against the training set
     * and then assign a classification to each unlabeled instance.
     */
    public void buildAndClassify() {
        try {

            logger.info("Building classifier {}...",
                    this.classifier.getClassifier().getClass().getSimpleName());
            this.classifier.buildClassifier(this.training_data);

            this.loadData(false);

            for (Instance i : this.classification_data) {
                double classification = this.classifier.classifyInstance(i);

                logger.debug("Classification: id: {}, class: {}",
                        (int) i.value(this.training_data.attribute(Storage.ID).index()),
                        this.training_data.classAttribute().value((int) classification)
                );
            }
        } catch (Exception e) {
            logger.error("Error while classifying new instances.", e);
        }
    }

    /**
     * Build the classifier against the training data and evaluate it.
     *
     * @param evaluation_rate parameter specifying the evaluation type.
     *                        If 0 &lt; evaluation_rate &lt; 1,
     *                        we'll use evaluation_rate percentage of the
     *                        dataset as test.
     *                        Otherwise, we'll use evaluation_rate-fold-validation.
     * @return the evaluation of the classifier.
     */
    public Evaluation buildAndEvaluate(float evaluation_rate) {
        assert evaluation_rate > 0;

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
            logger.error("Error while evaluating the classifier", e);
        }

        return eval;
    }
}

package com.github.aldurd392.UnitedTweetsAnalyzer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.UpdateableClassifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.classifiers.functions.LibSVM;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.lazy.KStar;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.rules.PART;
import weka.classifiers.trees.*;
import weka.core.*;
import weka.core.stopwords.StopwordsHandler;
import weka.experiment.InstanceQuery;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NominalToString;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Dynamically load and configure a Machine Learner based on a name.
 * Train it and evaluate the obtained results,
 * or classify unknown instances.
 */
class Learner {
    private final static Logger logger = LogManager.getLogger(Learner.class.getSimpleName());

    private final static String LOCATION_PREFIX = "_LOCATION";

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
        map.put("reptree", REPTree.class);
        map.put("htree", HoeffdingTree.class);
        map.put("part", PART.class);
        map.put("random_tree", RandomTree.class);
        map.put("random_forest", RandomForest.class);
        map.put("decision_stump", DecisionStump.class);

        map.put("perceptron", MultilayerPerceptron.class);
        map.put("libsvm", LibSVM.class);
        map.put("smo", SMO.class); // LibSVM is faster.

        map.put("kstar", KStar.class);

        map.put("adaboost", AdaBoostM1.class);

        classifiers = Collections.unmodifiableMap(map);
    }

    private static final char CSV_DELIMITER = ';';
    private static final Object[] CSV_FILE_HEADER = {
            "id", "profile_url", "location", "lang", "utc_offset", "timezone", "country",
    };

    /**
     * We use this regex to split the command line into
     * a vector of Strings.
     * We compile it for performance reason.
     */
    private final static Pattern re_spaces = Pattern.compile("\\s+");

    /**
     * We'll keep the instances used for training here.
     */
    private Instances training_data = null;
    /**
     * We'll keep the unlabeled instances here.
     */
    private Instances classification_data = null;
    /**
     * We'll keep the classifier here.
     */
    private AbstractClassifier classifier = null;

    /**
     * Build a new learner
     *
     * @param classifier_name the name of the classifier used by the learner.
     * @param cl_config       Weka command line configuration for the learner.
     * @throws Exception on error.
     */
    public Learner(String classifier_name, String cl_config) throws Exception {
        super();

        this.classifierFactory(classifier_name, cl_config);
    }

    public Instances getTrainingData() {
        return this.training_data;
    }

    public AbstractClassifier getClassifier() {
        return this.classifier;
    }

    /**
     * Set up the instances in input, setting up the class attribute
     * and if specified it apply the input filter.
     *
     * @param instances to set up.
     * @param filters   if set applies the filters on the input instances
     *                  (in order).
     * @return the set up instances.
     */
    private Instances setUpData(Instances instances, Filter[] filters) {
        Instances newInstances = instances;

        if (filters != null) {
            for (Filter filter : filters) {
                try {
                    filter.setInputFormat(newInstances);
                    newInstances = Filter.useFilter(newInstances, filter);
                } catch (Exception e) {
                    logger.warn(
                            "Cannot apply specified filter {}. Ignoring it.", filter.toString(),
                            e
                    );
                }
            }
        }

        newInstances.setClass(newInstances.attribute(Storage.COUNTRY));
        return newInstances;
    }

    /**
     * Load data from the DB and store them in instance variables.
     * Note that we always randomize the order of the retrieved instances.
     * <p>
     * When we want to classify unlabeled instances, we have to make sure that
     * the classifier knows the entire universe of attribute's values.
     * Because of this, we load with a single query all the data we needs,
     * and the we spit them.
     * In this way the headers of the Instances set will contain the correct
     * information.
     * In addition, we convert the "location" attribute to a vector of words.
     * It is nominal in our dataset, so we convert it to a string and then we
     * split it.
     *
     * @param isTraining if set we are loading training instances.
     *                   Otherwise, we're loading both training and unlabeled instances.
     * @throws Exception on error.
     */
    private void loadData(boolean isTraining) throws Exception {
        logger.info("Loading {} data.", isTraining ? "training" : "training and unlabeled");

        InstanceQuery query = null;
        try {
            query = new InstanceQuery();

            NominalToString nomToStringFilter = new NominalToString();
            StringToWordVector stringFilter = new StringToWordVector();
            NumericToNominal numericToNominal = new NumericToNominal();

            stringFilter.setDoNotOperateOnPerClassBasis(true);
            stringFilter.setOutputWordCounts(false);
            final int wordsToKeep = 500;
            stringFilter.setWordsToKeep(wordsToKeep);
            stringFilter.setStemmer(null);
            stringFilter.setAttributeNamePrefix(LOCATION_PREFIX);
            stringFilter.setIDFTransform(true);

            /**
             * Remove from the WordVector all those words with length 1.
             */
            stringFilter.setStopwordsHandler(new StopwordsHandler() {
                @Override
                public boolean isStopword(String word) {
                    return word.length() <= 1 ||
                            (word.length() == 2 && !Constants.countryCodes.contains(word)) ||
                            Constants.stopWords.contains(word);
                }
            });

            Filter[] filters = {nomToStringFilter, stringFilter, numericToNominal};

            String locationAttributeString;
            if (isTraining) {
                query.setQuery(Storage.TRAINING_QUERY);
                Instances instances = query.retrieveInstances();

                locationAttributeString = String.format(
                        "%d", instances.attribute(Storage.LOCATION).index() + 1
                );

                nomToStringFilter.setAttributeIndexes(
                        locationAttributeString
                );

                stringFilter.setAttributeIndices(
                        locationAttributeString
                );

                numericToNominal.setAttributeIndicesArray(
                        new int[]{
                                instances.attribute(Storage.UTC_OFFSET).index(),
                                instances.attribute(Storage.LANG).index(),
                                instances.attribute(Storage.TIMEZONE).index(),
                                instances.attribute(Storage.COUNTRY).index(),
                        }
                );
                numericToNominal.setInvertSelection(true);

                this.training_data = setUpData(instances, filters);
                this.training_data.randomize(new Random());

                assert (this.training_data.numAttributes() > 3 + wordsToKeep) :
                        "StringToWordVector doesn't seem to be working!";

                for (Attribute attribute : Collections.list(this.training_data.enumerateAttributes())) {
                    logger.debug(attribute.name());
                }
            } else {
                query.setQuery(Storage.CLASSIFICATION_QUERY);
                Instances universe = query.retrieveInstances();

                locationAttributeString = String.format(
                        "%d", universe.attribute(Storage.LOCATION).index() + 1
                );

                nomToStringFilter.setAttributeIndexes(
                        locationAttributeString
                );

                stringFilter.setAttributeIndices(
                        locationAttributeString
                );

                numericToNominal.setAttributeIndicesArray(
                        new int[]{
                                universe.attribute(Storage.ID).index(),
                                universe.attribute(Storage.UTC_OFFSET).index(),
                                universe.attribute(Storage.LANG).index(),
                                universe.attribute(Storage.TIMEZONE).index(),
                                universe.attribute(Storage.COUNTRY).index(),
                        }
                );
                numericToNominal.setInvertSelection(true);

                /**
                 * Load the whole universe of data:
                 * training and unlabeled.
                 */
                universe = setUpData(universe, filters);
                assert (universe.attribute(Storage.UTC_OFFSET).type() == 1) : "Got bad types from database";

                this.training_data = new Instances(universe, universe.numInstances() - 200);
                this.classification_data = new Instances(universe, 200);
                final Attribute class_attribute = universe.classAttribute();

                /**
                 * Split the data in training and unlabeled.
                 * We could use a filter, but this is more efficient in main memory.
                 */
                for (int i = universe.numInstances() - 1; i >= 0; i--) {
                    Instance instance = universe.instance(i);
                    universe.delete(i);

                    if (Utils.isMissingValue(instance.value(class_attribute))) {
                        this.classification_data.add(instance);
                    } else {
                        this.training_data.add(instance);
                    }
                }

                assert (this.classification_data.numInstances() <= Constants.classification_limit) :
                        "Bad number of classification data (" +
                                this.classification_data.numInstances() +
                                "), filter is likely to be not working.";

                assert this.training_data.equalHeadersMsg(this.classification_data) == null :
                        "Bad instances headers: " + this.training_data.equalHeadersMsg(this.classification_data);

                /**
                 * Remove the ID from the training data.
                 */
                Remove remove = new Remove();
                remove.setAttributeIndices(String.format("%d", this.training_data.attribute(Storage.ID).index() + 1));
                remove.setInputFormat(this.training_data);
                this.training_data = Filter.useFilter(this.training_data, remove);
                this.training_data.randomize(new Random());

                assert this.training_data.numAttributes() == this.classification_data.numAttributes() - 1 :
                        "Training data filtering is not working, bad number of attributes.";
                assert this.training_data.attribute(Storage.ID) == null :
                        "ID attributes can still be found after filtering!";
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

    /**
     * Setup the classifier parameters'.
     */
    private void setupLearner() {
        logger.info("Applying default configuration to {}", this.classifier.getClass().getSimpleName());

        if (this.classifier instanceof J48) {
            J48 j48 = (J48) this.classifier;

            j48.setCollapseTree(false);
            j48.setBinarySplits(false);
            j48.setUnpruned(false);
            j48.setReducedErrorPruning(false);
            j48.setConfidenceFactor(0.25f);
            j48.setUseLaplace(true);
            j48.setNumFolds(5);
            j48.setSubtreeRaising(false);
        } else if (this.classifier instanceof LibSVM) {
            LibSVM libSVM = (LibSVM) this.classifier;

            libSVM.setCacheSize(512); // MB
            libSVM.setNormalize(true);
            libSVM.setShrinking(true);
            libSVM.setKernelType(new SelectedTag(LibSVM.KERNELTYPE_POLYNOMIAL, LibSVM.TAGS_KERNELTYPE));
            libSVM.setDegree(3);
            libSVM.setSVMType(new SelectedTag(LibSVM.SVMTYPE_C_SVC, LibSVM.TAGS_SVMTYPE));
        } else if (this.classifier instanceof NaiveBayes) {
            NaiveBayes naiveBayes = (NaiveBayes) this.classifier;

            // Configure NaiveBayes
            naiveBayes.setUseKernelEstimator(false);
            naiveBayes.setUseSupervisedDiscretization(false);
        } else if (this.classifier instanceof RandomForest) {
            RandomForest rndForest = (RandomForest) this.classifier;

            // Configure RandomForest
            rndForest.setNumExecutionSlots(5);
            rndForest.setNumTrees(50);
            rndForest.setMaxDepth(3);
        } else if (this.classifier instanceof MultilayerPerceptron) {
            MultilayerPerceptron perceptron = (MultilayerPerceptron) this.classifier;

            // Configure perceptron
            perceptron.setAutoBuild(true);
            perceptron.setTrainingTime(250); // epochs
            perceptron.setNominalToBinaryFilter(false);
            perceptron.setNormalizeAttributes(true);
        }
    }

    /**
     * Instantiate a classifier from it's name.
     *
     * @param classifier_name the name of the classifier to be instantiated.
     * @param cl_config       Weka configuration for the learner.
     *                        This overrides the setup in {@link #setupLearner()}.
     * @throws Exception on instantiation error.
     */
    private void classifierFactory(String classifier_name, String cl_config) throws Exception {
        Class<? extends AbstractClassifier> classifier_class = Learner.classifiers.get(classifier_name);

        if (classifier_class == null) {
            final String error = "Unknown classifier name " + classifier_name;
            logger.error(error);
            throw new Exception(error);
        }

        try {
            Constructor<? extends AbstractClassifier> constructor = classifier_class.getConstructor();
            AbstractClassifier abstractClassifier = constructor.newInstance();

            this.classifier = abstractClassifier;
            setupLearner();

            if (cl_config != null) {
                /**
                 * Set the command line options.
                 */
                abstractClassifier.setOptions(re_spaces.split(cl_config));
            }
        } catch (NoSuchMethodException |
                InvocationTargetException |
                InstantiationException |
                IllegalAccessException e) {
            logger.error("Error while instantiating classifier {}", classifier_class.getSimpleName(), e);
            throw e;
        }

        logger.debug("Classifier {} correctly created.", this.classifier.getClass().getSimpleName());
    }

    /**
     * If evaluating by using a percentage of the dataset as test,
     * this function split the data as required.
     *
     * @param percentage_split parameter indicating the percentage of test data.
     * @return An entry containing, in order, training and test sets.
     */
    private Map.Entry<Instances, Instances> splitTrainingTestData(float percentage_split) {
        assert (percentage_split > 0 && percentage_split < 1);

        final int trainingSize = (int) Math.round(this.training_data.numInstances() * (1.0 - percentage_split));
        final int testingSize = this.training_data.numInstances() - trainingSize;

        final Instances train = new Instances(this.training_data, 0, trainingSize);
        final Instances test = new Instances(this.training_data, trainingSize, testingSize);

        return new AbstractMap.SimpleEntry<>(train, test);
    }

    /**
     * Train the classifier on the given instances.
     *
     * @param training_data the instances to use.
     * @throws Exception if the classifier encounters and error while being built.
     */
    private void trainClassifier(Instances training_data) throws Exception {
        if (this.classifier instanceof UpdateableClassifier) {
            logger.info("Building updateable classifier.");
            UpdateableClassifier classifier = (UpdateableClassifier) this.classifier;

            /**
             * We always have to call @{@link AbstractClassifier#buildClassifier(Instances)}.
             * We thus build a new Instances set from the dataset.
             * Then, we start feeding the classifier.
             */
            this.classifier.buildClassifier(new Instances(training_data, 0));

            for (int i = training_data.numInstances() - 1; i >= 0; i--) {
                Instance instance = training_data.instance(i);
                training_data.delete(i);

                classifier.updateClassifier(instance);
            }
        } else {
            this.classifier.buildClassifier(training_data);
        }
    }

    /**
     * Classify unseen instances.
     * It builds the classifier against the training set
     * and then assign a classification to each unlabeled instance.
     *
     * @param output_path optional path to store a CSV file with the results.
     */
    public void buildAndClassify(String output_path) {
        try {
            this.loadData(false);
        } catch (Exception e) {
            logger.fatal("Error while loading training and unlabeled data", e);
            return;
        }

        CSVPrinter csvFilePrinter = null;
        FileWriter fileWriter = null;

        if (output_path != null) {
            final CSVFormat csvFileFormat = CSVFormat.EXCEL.withDelimiter(CSV_DELIMITER);

            try {
                fileWriter = new FileWriter(output_path);
                csvFilePrinter = new CSVPrinter(fileWriter, csvFileFormat);
                csvFilePrinter.printRecord(CSV_FILE_HEADER);
            } catch (IOException e) {
                logger.warn("Error while creating CSV file printer", e);

                IOUtils.closeQuietly(fileWriter);
                IOUtils.closeQuietly(csvFilePrinter);
            }
        }

        try {
            logger.info("Building classifier {}...",
                    this.classifier.getClass().getSimpleName());
            this.trainClassifier(this.training_data);
        } catch (Exception e) {
            logger.fatal("Error while building classifier for new instances.", e);

            IOUtils.closeQuietly(fileWriter);
            IOUtils.closeQuietly(csvFilePrinter);

            return;
        }

        final Attribute attribute_id = this.classification_data.attribute(Storage.ID);
        final Attribute attribute_lang = this.classification_data.attribute(Storage.LANG);
        final Attribute attribute_utc_offset = this.classification_data.attribute(Storage.UTC_OFFSET);
        final Attribute attribute_timezone = this.classification_data.attribute(Storage.TIMEZONE);

        Remove remove;
        try {
            remove = new Remove();
            remove.setAttributeIndices(String.format("%d", attribute_id.index() + 1));
            remove.setInputFormat(this.classification_data);
        } catch (Exception e) {
            logger.error("Error while building remove filter for unlabeled instances", e);
            return;
        }

        for (Instance i : this.classification_data) {
            remove.input(i);
            Instance trimmedInstance = remove.output();

            final long id = Double.valueOf(i.value(attribute_id)).longValue();
            double classification;

            try {
                classification = this.classifier.classifyInstance(trimmedInstance);
            } catch (Exception e) {
                logger.warn("Classification - id: {}, class: UNAVAILABLE",
                        id
                );
                logger.error("Error while classifying unlabeled instance", e);
                return;
            }

            StringBuilder location = new StringBuilder();
            for (Attribute attribute : Collections.list(i.enumerateAttributes())) {
                if (attribute.name().startsWith(LOCATION_PREFIX) &&
                        i.value(attribute) > 0) {
                    location.append(attribute.name().substring(LOCATION_PREFIX.length()));
                    location.append(" ");
                }
            }


            final Object[] values = {
                    id,
                    String.format(Constants.twitter_user_intent, id),
                    location.toString(),
                    i.stringValue(attribute_lang),
                    i.stringValue(attribute_utc_offset),
                    i.stringValue(attribute_timezone),
                    this.training_data.classAttribute().value((int) classification),
            };

            logger.debug("Classification - {} -> {}", i.toString(), values[6]);
            if (csvFilePrinter != null) {
                try {
                    csvFilePrinter.printRecord(values);
                } catch (IOException e) {
                    logger.error("Error while printing CSV records for ID {}", id, e);
                }
            }
        }

        IOUtils.closeQuietly(fileWriter);
        IOUtils.closeQuietly(csvFilePrinter);
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
        try {
            this.loadData(true);
        } catch (Exception e) {
            logger.error("Error while loading training data.", e);
            return null;
        }

        Evaluation eval = null;
        assert evaluation_rate > 0;
        try {
            if (evaluation_rate < 1) {
                logger.info("Building and evaluating classifier {} with testing percentage {}...",
                        this.classifier.getClass().getSimpleName(), evaluation_rate);

                Map.Entry<Instances, Instances> data = splitTrainingTestData(evaluation_rate);

                this.trainClassifier(data.getKey());

                eval = new Evaluation(data.getKey());
                eval.evaluateModel(this.classifier, data.getValue());
            } else {
                eval = new Evaluation(this.training_data);
                int rounded_evaluation_rate = Math.round(evaluation_rate);

                logger.info("Building and evaluating classifier {} with {}-fold validation...",
                        this.classifier.getClass().getSimpleName(),
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


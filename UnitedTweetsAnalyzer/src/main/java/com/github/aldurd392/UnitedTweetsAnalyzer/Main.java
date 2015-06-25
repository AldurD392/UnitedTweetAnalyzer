
package com.github.aldurd392.UnitedTweetsAnalyzer;

import org.apache.commons.cli.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class Main {
    private static final String EXECUTABLE_NAME = "UnitedTweetsAnalyzer";

    private static final String DEFAULT_DATABASE_PATH = "users.db";
    private static final String DEFAULT_EVALUATION_RATE = "0.3";

    private static final String TASK = "t";
    private static final String SHAPEFILE = "s";
    private static final String LEARNER_NAME = "l";
    private static final String EVALUATION_RATE = "e";
    private static final String DATABASE_PATH = "d";
    private static final String HELP = "h";

    private static final String[] TASK_TYPE = {
            "store",
            "learn"
    };
    private static final String LEARN_ALL = "all";

    private static Options createOptions() {
        // Create the Options
        Options options = new Options();

        Option task = Option.builder(TASK)
                .longOpt("task")
                .desc("set the task type " + Arrays.toString(TASK_TYPE))
                .hasArg(true)
                .required(false)
                .type(String.class)
                .build();
        options.addOption(task);

        Option shapefile_path = Option.builder(SHAPEFILE)
                .longOpt("shapefile")
                .desc("shapefile path")
                .hasArg(true)
                .required(false)
                .type(String.class)
                .build();
        options.addOption(shapefile_path);

        {
            Set<String> classifiers = new HashSet<>(Learner.classifiers.keySet());
            classifiers.add(LEARN_ALL);

            Option learner_name = Option.builder(LEARNER_NAME)
                    .longOpt("learner_name")
                    .desc("name of the classifier " + classifiers)
                    .hasArg(true)
                    .required(false)
                    .type(String.class)
                    .build();
            options.addOption(learner_name);
        }

        Option evaluation_rate = Option.builder(EVALUATION_RATE)
                .longOpt("evaluation rate")
                .desc("specify the evaluation rate; " +
                                "0 < values < 1 will let the evaluator use a percentage of the training data as tests; " +
                                "values > 1 will let the evaluator use value-fold cross validation"
                )
                .hasArg(true)
                .required(false)
                .type(Float.class)
                .build();
        options.addOption(evaluation_rate);

        Option database_path = Option.builder(DATABASE_PATH)
                .longOpt("database")
                .desc("database path")
                .hasArg(true)
                .required(false)
                .type(String.class)
                .build();
        options.addOption(database_path);

        Option help = Option.builder(HELP)
                .longOpt("help")
                .desc("print this help")
                .hasArg(false)
                .required(false)
                .type(Boolean.class)
                .build();
        options.addOption(help);

        return options;
    }

    public static void main(String[] args) throws Exception {
        // Create the command line parser
        CommandLineParser parser = new DefaultParser();
        Options options = createOptions();

        try {
            // Parse the command line arguments
            CommandLine commandLine = parser.parse(options, args);

            if (commandLine.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(EXECUTABLE_NAME, options);

                System.exit(0);
            }

            // Validate the arguments
            if (!commandLine.hasOption(TASK)) {
                throw new ParseException("missing required option -" +  TASK);
            }

            String database_path = commandLine.getOptionValue(DATABASE_PATH, DEFAULT_DATABASE_PATH);

            String value = commandLine.getOptionValue(TASK);
            if (TASK_TYPE[0].equals(value)) {
                String shapefile_path = commandLine.getOptionValue(SHAPEFILE);
                if (shapefile_path == null) {
                    throw new ParseException(
                            "-" + TASK + " " + TASK_TYPE[0] + " requires a shapefile (-" + SHAPEFILE + ")"
                    );
                }

                Geography geography = new Geography(shapefile_path);
                Storage storage = new Storage(geography, database_path);
                Streamer streamer = new Streamer(storage);
                streamer.startListening();
            } else if (TASK_TYPE[1].equals(value)) {
                String classifier_name = commandLine.getOptionValue(LEARNER_NAME);
                if (classifier_name == null) {
                    throw new ParseException(
                            "-" + TASK + " " + TASK_TYPE[1] + "requires a classifier name (-" + LEARNER_NAME + ")"
                    );
                } else if (!classifier_name.equals(LEARN_ALL) && !Learner.classifiers.containsKey(classifier_name)) {
                    throw new ParseException("Invalid classifier name " + classifier_name);
                }

                String evaluation_rate_string = commandLine.getOptionValue(EVALUATION_RATE, DEFAULT_EVALUATION_RATE);
                ParseException bad_evaluation_rate = new ParseException("Invalid evaluation value " + evaluation_rate_string);

                float evaluation_rate;
                try {
                    evaluation_rate = Float.parseFloat(evaluation_rate_string);
                    if (evaluation_rate <= 0 || (evaluation_rate >= 1 && evaluation_rate < 2)) {
                        throw bad_evaluation_rate;
                    }
                } catch (NumberFormatException e) {
                    throw bad_evaluation_rate;
                }

                if (classifier_name.equals(LEARN_ALL)) {
                    for (String classifier : Learner.classifiers.keySet()) {
                        new Learner(classifier).buildAndEvaluate(evaluation_rate);
                    }
                } else {
                    new Learner(classifier_name).buildAndEvaluate(evaluation_rate);
                }
            } else {
                throw new ParseException(value + "is not a valid value for -" + TASK);
            }
        } catch (ParseException exp) {
            // Automatically generate the help statement
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(EXECUTABLE_NAME, options);

            System.out.println("Parse exception: " + exp.getMessage());
            System.exit(1);
        }
    }
}

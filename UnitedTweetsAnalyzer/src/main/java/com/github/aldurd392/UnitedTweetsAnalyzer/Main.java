
package com.github.aldurd392.UnitedTweetsAnalyzer;

import org.apache.commons.cli.*;


public class Main {
    private static final String EXECUTABLE_NAME = "UnitedTweetsAnalyzer";

    private static final String DEFAULT_DATABASE_PATH = "users.db";
    private static final String DEFAULT_EVALUATION_RATE = "0.3";

    private static Options createOptions() {
        // Create the Options
        Options options = new Options();

        Option task = Option.builder("t")
                .longOpt("task")
                .desc("set the task type [store, learn]")
                .hasArg(true)
                .required(false)
                .type(String.class)
                .build();
        options.addOption(task);

        Option shapefile_path = Option.builder("s")
                .longOpt("shapefile")
                .desc("shapefile path")
                .hasArg(true)
                .required(false)
                .type(String.class)
                .build();
        options.addOption(shapefile_path);

        Option learner_name = Option.builder("l")
                .longOpt("learner_name")
                .desc("name of the classifier " + Learner.classifiers.keySet().toString())
                .hasArg(true)
                .required(false)
                .type(String.class)
                .build();
        options.addOption(learner_name);

        Option evaluation_rate = Option.builder("e")
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

        Option database_path = Option.builder("d")
                .longOpt("database")
                .desc("database path")
                .hasArg(true)
                .required(false)
                .type(String.class)
                .build();
        options.addOption(database_path);

        Option help = Option.builder("h")
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
            if (!commandLine.hasOption("t")) {
                throw new ParseException("missing required option -t");
            }

            String database_path = commandLine.getOptionValue("d");
            if (database_path == null) {
                database_path = DEFAULT_DATABASE_PATH;
            }

            String value = commandLine.getOptionValue("t");
            if ("store".equals(value)) {
                String shapefile_path = commandLine.getOptionValue("s");
                if (shapefile_path == null) {
                    throw new ParseException("-t \"store\" requires a shapefile (-s)");
                }

                Geography geography = new Geography(shapefile_path);
                Storage storage = new Storage(geography, database_path);
                Streamer streamer = new Streamer(storage);
                streamer.startListening();
            } else if ("learn".equals(value)) {
                String classifier_name = commandLine.getOptionValue("l");
                if (classifier_name == null) {
                    throw new ParseException("-t \"learn\" requires a classifier name (-l)");
                } else if (!Learner.classifiers.containsKey(classifier_name)) {
                    throw new ParseException("Invalid classifier name " + classifier_name);
                }

                String evaluation_rate_string = commandLine.getOptionValue("e", DEFAULT_EVALUATION_RATE);
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

                Learner learner = new Learner(classifier_name);
                learner.buildClassifier();
                learner.evaluateClassifier(evaluation_rate);
            } else {
                throw new ParseException(value + "is not a valid value for -t");
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

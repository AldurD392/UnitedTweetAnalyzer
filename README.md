# United Tweet Analyzer
Machine Learning - Web & Social classes, joint project, 2015.

## Install
UnitedTweetsAnalyzer is a Maven project.
You can install it (i.e. build the JAR package) as follows.

```bash
$ cd UnitedTweetsAnalyzer
$ mvn package
```

You'll find the generated JAR in the `target` directory.
Specifically there will be two files of interest:
* A standalone file (`UnitedTwitterAnalyzer-jar-with-dependencies.jar`).
* A maven executable file (`UnitedTwitterAnalyzer.jar`).

Packaging the project will also run the unit tests.
You can disable them with the `-Dmaven.test.skip=true` flag.

#### Javadoc
By using the following command you can build the Javadoc.

```bash
$ cd UnitedTweetsAnalyzer
$ mvn javadoc:javadoc
```

The generated index file lies in `target/site/apidocs/index.html`.
Furthermore, the source code is heavily commented.

## Configuration
This project requires a valid Twitter API key in order to run.

### Twitter OAuth
Edit/create the `twitter4j.properties` file within the `UnitedTweetsAnalyzer` directory and enter yours before continuing.
More detailed docs related to the Twitter4J configuration are available [here](http://twitter4j.org/en/configuration.html).

### Assertion checks
We've made use of Java assertions in our code.
You can enable them by passing the `-ea` flag to the `java` command.
We strongly advise it.

### Java VM heap size
Some of the machine learning tasks require a great heap space.
You can set it up by passing the `-Xms3g` flag to the `java` command.
In this example we're setting an heap size of 3GB.

## Run
### Command line options
You can check all the available command line options with the `-h` flag.
As an example:

```bash
$ java -jar target/UnitedTwitterAnalyzer-jar-with-dependencies.jar -h

usage: UnitedTweetsAnalyzer
 -b,--stream_bias <arg>       bias applied to the stream [geo, all]
 -e,--evaluation rate <arg>   specify the evaluation rate; 0 < values < 1
                              will let the evaluator use a percentage of
                              the training data as tests; values > 1 will
                              let the evaluator use value-fold cross
                              validation
 -h,--help                    print this help
 -l,--learner_name <arg>      name of the classifier [random_forest, all,
                              perceptron, libsvm, dtree, nbayes]
 -o,--output path <arg>       specify an optional output path for the
                              unsupervised classification results
 -s,--shapefile <arg>         shapefile path
 -t,--task <arg>              set the task type [store, learn, classify]
```

### Task types
As you can see, you have to specify a task to be executed.
Three tasks are available:

* Store: collect training data from the Twitter stream.
* Learn: train and evaluate a model by using previously acquired ground truth.
* Classify: classify unlabeled instances.

Each one of those tasks requires different command line configuration.
We'll provide some examples here.

#### Store task
This task requires a shapefile path (`-s` flag): i.e. a valid `.shp` and its related metadata (`.shx`, and so on).
As an example:

```bash
$ java -jar target/UnitedTwitterAnalyzer-jar-with-dependencies.jar -t store -s shapefile.shp
```

If you need to listen to an unbiased sample of the Twitter stream (e.g. to acquire unlabeled data), you can supply the `-b all` flag.

```bash
$ java -jar target/UnitedTwitterAnalyzer-jar-with-dependencies.jar -t store -s shapefile.shp -b all
```

#### Learn task
Once you have acquired enough data you can build and evaluate a classifier.
The `learn` task is your friend.

As an example, to build a Naive Bayes classifier, you could use:

```bash
$ java -jar target/UnitedTwitterAnalyzer-jar-with-dependencies.jar -t learn -l nbayes
```

The `-l` flag accepts the special `all` keyword to build and train all the available classifiers and print out the best one.

The `-e` flag lets you specify the evaluation type.
Values between 0 and 1 will specify the use of a portion of the dataset as test data (percentage split).
Values greater than 1 will enable k-fold cross-validation.

#### Classify task
This task lets you label new instances.
It will sample them from those in our database who don't have an associated geographic position.

As an example:

```bash
$ java -jar target/UnitedTwitterAnalyzer-jar-with-dependencies.jar -t classify -l nbayes -o output.csv
```

Note that, by supplying the `-o` flag, we're storing the classification output in an Excel readable CSV file.

## Experimental results
We've experimented with lot of different settings.
We'll report here some of our experimental results.

## Authors
Adriano Di Luzio & Danilo Francati

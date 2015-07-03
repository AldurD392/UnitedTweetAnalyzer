# United Tweet Analyzer
Machine Learning - Web & Social classes, joint project, 2015.

Goals:
* Given a bounding box and a shapefile, listen to the Twitter stream of Tweets having a geographic position that falls within the bounding box.
Then, assign a region (country, city and so on) to those tweets, by using the shapefile.
* Given the ground truth (a dataset of users, their tweets and the relative position/region), learn a Machine Learning function able to classify unseen data.
* Test and evaluate different algorithms. Find out the precision of the best one while classifying unlabeled data.

## Install
UnitedTweetsAnalyzer ships as a Maven project. We've tested it against Java 8 and should be fine with every version >= 7.
You can install it (i.e. build the JAR package) as follows.

```bash
$ git clone https://github.com/AldurD392/UnitedTweetAnalyzer  # or download the repository as ZIP
$ cd UnitedTweetsAnalyzer/UnitedTweetsAnalyer
$ mvn package
```

You'll find the generated JAR in the `target` directory.
Specifically there will be two files of interest:
* A standalone file (`UnitedTwitterAnalyzer-jar-with-dependencies.jar`).
* A maven executable file (`UnitedTwitterAnalyzer.jar`). Execute it with `mvn exec:java -Dexec.args="COMMAND_LINE_OPTIONS"`.

Packaging the project will also run the unit tests.
You can disable them with the `-Dmaven.test.skip=true` flag.

#### Javadoc
By using the following command you can build the Javadoc.

```bash
$ cd UnitedTweetsAnalyzer
$ mvn javadoc:javadoc
```

The generated index file lies in `target/site/apidocs/index.html`.
In addition to the docs, the source code should be readable and heavily commented.

## Configuration
This project requires a valid Twitter API key in order to run (specifically, it is needed to launch the Store task, see later).

### Twitter OAuth
Edit/create the `twitter4j.properties` file within the `UnitedTweetsAnalyzer` directory and enter your OAuth tokens before continuing.
More detailed docs related to the Twitter4J configuration are available [here](http://twitter4j.org/en/configuration.html).

We also like to disable debug and Twitter4J's logger:

```
# twitter4j.properties

loggerFactory=twitter4j.NullLoggerFactory
debug=false 
```

### Assertion checks
We've made a large use of the Java assertions in our code.
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
 -b,--stream_bias <arg>       bias applied to the stream [all, geo,
                              all_geo]
 -c,--learner_cl <arg>        specify the Weka-like configuration of the
                              learner
 -e,--evaluation rate <arg>   specify the evaluation rate; 0 < values < 1
                              will let the evaluator use a percentage of
                              the training data as tests; values > 1 will
                              let the evaluator use value-fold cross
                              validation
 -h,--help                    print this help
 -l,--learner_name <arg>      name of the classifier [random_forest, all,
                              perceptron, libsvm, kstar, adaboost, part,
                              decision_stump, smo, dtree, htree,
                              random_tree, reptree, nbayes]
 -o,--output path <arg>       specify an optional output path for the
                              unsupervised classification results
 -s,--shapefile <arg>         shapefile path
 -t,--task <arg>              set the task type [store, learn, classify]
```

The `-t` option is always required.

### Task types
As you can see, you have to specify a task to be executed.
Three tasks are available:

* Store: collect training data from the Twitter stream.
* Learn: train and evaluate a model by using previously acquired ground truth.
* Classify: classify unlabeled instances.

Each one of those tasks requires different command line configuration.
We'll provide some examples here.

Please note that before being able to Learn and / or Classify instances, you need to launch at least once (many seconds are enough) the Store task, in order to gather the necessary training and unlabeled data.

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

Alternatively, you can bias the stream to acquire only those tweets having an attached location (`-b all_geo`).

This is a forever-running task. You can stop it anytime you want by pressing `CTRL-c`.

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

The `-c` flag lets you specify the command line arguments for the learner.
It is ignored with `-l all`.

#### Classify task
This task lets you label new instances.
It will sample them from those in our database who don't have an associated geographic position.

As an example:

```bash
$ java -jar target/UnitedTwitterAnalyzer-jar-with-dependencies.jar -t classify -l nbayes -o output.csv
```

Note that, by supplying the `-o` flag, we're storing the classification output in an Excel readable CSV file.
The `-c` flag is also supported here (see previous task).

## Experimental results
We've experimented with lot of different settings and we'll report here some of our experimental results.

### Dataset
We've set the bounding box to the one containing the whole USA.
We've gathered a dataset of 700k users and 800k tweets.
The tweets represent our ground truth, because they have an associated region and are linked to a Twitter user.

Among those tweets, ~400k had an associated USA region.
The remaining, on the other side, were foreign.
Both those information have been exploited by our algorithms in order to learn when to classify a user inside one of the USA countries or outside the states.
Note that the size of USA/foreign tweets is roughly the same. We'll be in fact testing the precision of the algorithms on unsupervised data, i.e. a sample of the entire tweet stream. As a consequence, it's more likely to see someone from the whole world than from the USA, and the algorithms has to consequently react.

Our experiments were often limited by the size of our physical main memory, but we've stretched the performance of our system to the maximum.

### Naive Bayes
The following evaluation statistics have been obtained by using 10-fold cross validation.

```
Correctly Classified Instances      512926               65.9836 %
Incorrectly Classified Instances    264428               34.0164 %
Kappa statistic                          0.5196
Mean absolute error                      0.0179
Root mean squared error                  0.0954
Relative absolute error                 59.7094 %
Root relative squared error             77.8616 %
Coverage of cases (0.95 level)          94.9072 %
Mean rel. region size (0.95 level)      24.3993 %
Total Number of Instances           777354
```

As you can see, we've got a pretty accuracy of 66%.

### Hoeffding Tree
This classifier performs similarly to (slightly better than, actually) Naive Bayes, on which it internally relies.
Supplying even more data to this classifier would probably improve the results.

```
Correctly Classified Instances      563836               72.5327 %
Incorrectly Classified Instances    213518               27.4673 %
Kappa statistic                          0.5792
Mean absolute error                      0.0283
Root mean squared error                  0.1128
Relative absolute error                 94.2586 %
Root relative squared error             92.0916 %
Coverage of cases (0.95 level)          97.164  %
Mean rel. region size (0.95 level)      78.072  %
Total Number of Instances           777354
```

### Adaboost
Adaboost is faster, but produces slightly worst results:

```
Correctly Classified Instances      377407               48.5502 %
Incorrectly Classified Instances    399947               51.4498 %
Kappa statistic                          0
Mean absolute error                      0.0258
Root mean squared error                  0.1136
Relative absolute error                 85.9708 %
Root relative squared error             92.7215 %
Coverage of cases (0.95 level)          95.9657 %
Mean rel. region size (0.95 level)      44.1019 %
Total Number of Instances           777354
```

### Other classifiers
Our system includes, out-of-the-box, a great number of classifiers and can be easily extended.
We've tested all those classifiers, but we couldn't manage to gather enough main memory space to report their results with our whole dataset.
Anyway, we'd be happy to hear from you. :)

### Performance
We've chosen by design and for simplicity, to store our data in an SQLite database.
It suits our needs, because it can be easily handled by Maven without any other dependency, but is slow while querying data in our big dataset (a Users-Tweets joint query takes ~10 seconds).
Switching to a faster database storage would dramatically improve the performance of the system but is beyond the scope of our project.

Storage aside, each learning algorithm comes with its own performances.
Nonetheless, our system is designed to exploit maximize the performance while minimizing the necessary memory.

## Authors
Adriano Di Luzio & Danilo Francati

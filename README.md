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
 -w,--learner_words <arg>     If set to a numeric value greater than 0,
                              while learning / classifying convert the
                              location attribute to a vector of words.The
                              default of 0 means that this feature is
                              disabled
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

The `-w` flag enable the LocationToWordsVector feature (see later).

#### Classify task
This task lets you label new instances.
It will sample them from those in our database who don't have an associated geographic position.

As an example:

```bash
$ java -jar target/UnitedTwitterAnalyzer-jar-with-dependencies.jar -t classify -l nbayes -o output.csv
```

Note that, by supplying the `-o` flag, we're storing the classification output in an Excel readable CSV file.
The `-c` and `-w` flags are also supported here (see previous task).

## Location attribute to vector of words
Supplying the `-w NUMBER` flag to either the learning or classification tasks enables the conversion of the users' location attribute (usually a nominal value) to a vector of words.
This implies many different things:

* The learning process employs now slightly different principles: each single word in the location attribute space is examined and ranked accordingly to its IDF. Only the best words are kept.
* Performance decrease: we have a number of attributes that is not fixed anymore, but is proportional to the value specified with the flag.
* Variable learner precision: greater number of words to keep imply greater learning precision and greater requirements in terms of time and space.

### When should I use this feature?
As always in this field, it depends on you needs.
This feature completely conforms to the state of the art techniques in this kind of classification task, but comes with a great performance cost.
As a consequence, it could weight toward one or the other the trade-off between learning precision and time/space performances.
Finally, please take a look to our experimental results.

We tried different configurations on our machines.
Because of their hardware limitations, we could not experiment with high values of the `-w` flag, having to stop at 700.
The results we found are slightly worst than the ones obtained with this feature disabled. 
Nonetheless, there is a clear indication that, by providing higher number of words to keep, we'd found better results.

## Experimental results
We've experimented with lot of different settings and we'll report here some of our experimental results.

### Dataset
We've set the bounding box to the one containing the whole USA.
We've gathered a dataset of 1kk users and 2kk tweets.
The tweets represent our ground truth, because they have an associated region and are linked to a Twitter user.

Among those tweets, ~700k had an associated USA region.
The remaining, on the other side, were foreign.
Both those information have been exploited by our algorithms in order to learn when to classify a user inside one of the USA countries or outside the states.
Note that the size of USA/foreign tweets is roughly the same. We'll be in fact testing the precision of the algorithms on unsupervised data, i.e. a sample of the entire tweet stream. As a consequence, it's more likely to see someone from the whole world than from the USA, and the algorithms has to consequently react.

Our experiments were often limited by the size of our physical main memory, but we've stretched the performance of our system to the maximum.

### Results w/o LocationToWordsVector
We've found the following results without enabling the LocationToWordsVector feature (default behaviour).

#### Naive Bayes
The following evaluation statistics have been obtained by using 30% of the dataset as tests.

```
Correctly Classified Instances      416483               75.1246 %
Incorrectly Classified Instances    137907               24.8754 %
Kappa statistic                          0.5399
Mean absolute error                      0.0132
Root mean squared error                  0.0824
Relative absolute error                 33.7708 %
Root relative squared error             58.8408 %
Coverage of cases (0.95 level)          95.9882 %
Mean rel. region size (0.95 level)      18.268  %
Total Number of Instances           554390
```

As you can see, we've got a pretty accuracy of 75%.

#### Hoeffding Tree
This classifier performs similarly to (slightly better than, actually) Naive Bayes, on which it internally relies.
Supplying even more data to this classifier would probably improve the results.

```
Correctly Classified Instances      446365               80.5146 %
Incorrectly Classified Instances    108025               19.4854 %
Kappa statistic                          0.6024
Mean absolute error                      0.0247
Root mean squared error                  0.1031
Relative absolute error                 63.0729 %
Root relative squared error             73.6531 %
Coverage of cases (0.95 level)          96.4226 %
Mean rel. region size (0.95 level)      75.3769 %
Total Number of Instances           554390
```

#### Adaboost
Adaboost is faster, but produces slightly worst results:

```
Correctly Classified Instances      355513               64.1269 %
Incorrectly Classified Instances    198877               35.8731 %
Kappa statistic                          0
Mean absolute error                      0.0195
Root mean squared error                  0.0987
Relative absolute error                 83.6947 %
Root relative squared error             91.461  %
Coverage of cases (0.95 level)          96.8358 %
Mean rel. region size (0.95 level)      34.8949 %
Total Number of Instances           554390
```

### Results w/ LocationToWordsVector
We have successfully experimented different values related to the number of words to keep.
We'll provide here many of our results, obtained with a parameter of words to keep set to 700.
Again, we've evaluated the classifiers by using 30% of the dataset as tests.

#### Hoeffding Tree

```
Correctly Classified Instances      403425               72.7692 %
Incorrectly Classified Instances    150965               27.2308 %
Kappa statistic                          0.4113
Mean absolute error                      0.0149
Root mean squared error                  0.0889
Relative absolute error                 38.0561 %
Root relative squared error             63.5091 %
Coverage of cases (0.95 level)          92.499  %
Mean rel. region size (0.95 level)      22.3975 %
Total Number of Instances           554390
```

As you can see, the accuracy of this classifier is slightly worsts than the one obtained without enabling the LocationToWordsVector feature.
Nonetheless, our experiments showed us that, by letting the classifier keep a greater number of words, the precision would have probably improved.

#### Naive Bayes

```
Correctly Classified Instances      372199               67.1367 %
Incorrectly Classified Instances    182191               32.8633 %
Kappa statistic                          0.4972
Mean absolute error                      0.0164
Root mean squared error                  0.0925
Relative absolute error                 41.7555 %
Root relative squared error             66.0487 %
Coverage of cases (0.95 level)          91.2598 %
Mean rel. region size (0.95 level)      20.7434 %
Total Number of Instances           554390   
```

Again, Naive Bayes if faster than Hoeffding Tree, but looses in terms of precision.

### Other classifiers / experiments
Our system includes, out-of-the-box, a great number of classifiers and can be easily extended.
We've tested all those classifiers, but we couldn't manage to gather enough main memory space to report their results with our whole dataset.
Anyway, we'd be happy to hear from you. :)

### Performance
We've chosen by design and for simplicity, to store our data in an SQLite database.
It suits our needs, because it can be easily handled by Maven without any other dependency, but is slow while querying data in our big dataset (a Users-Tweets joint query takes ~30 seconds).
Switching to a faster database storage would dramatically improve the performance of the system but is beyond the scope of our project.

Storage aside, each learning algorithm comes with its own performances.
Nonetheless, our system is designed to exploit maximize the performance while minimizing the necessary memory.

## Authors
Adriano Di Luzio & Danilo Francati

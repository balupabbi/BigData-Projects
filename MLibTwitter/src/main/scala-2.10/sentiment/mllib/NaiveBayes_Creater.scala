package sentiment.mllib

/**
 * Created by Bhargav on 12/2/16.
 */
import org.apache.hadoop.io.compress.GzipCodec
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.mllib.classification.{NaiveBayes, NaiveBayesModel}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.serializer.KryoSerializer
import org.apache.spark.sql._
import org.apache.spark.{SparkConf, SparkContext}
import sentiment.utils.{SQLContextSingleton, PropertiesLoader, StopwordsLoader, LogUtils}


//import org.p7h.spark.sentiment.utils._



object NaiveBayes_Creater {

  def main(args: Array[String]): Unit = {
    //create spark context
    val sc = createSparkContext()

    LogUtils.setLogLevels(sc)


    val stopWordsList = sc.broadcast(StopwordsLoader.loadStopWords(PropertiesLoader.NLTK_STOPWORDS_FILE_NAME)) //used broadcast because every machine needs to have nltkStopWords so its cached in all machines
    createAndSaveNBModel(sc, stopWordsList)
    validateAccuracyOfNBModel(sc, stopWordsList)


  }



  /**
   * Create SparkContext.
   * Future extension: enable checkpointing to HDFS [is it really reqd??].
   *
   * @return SparkContext
   */
  def createSparkContext(): SparkContext = {
    val conf = new SparkConf()
      .setAppName(this.getClass.getSimpleName)
      .setMaster("local[2]")
      .set("spark.serializer", classOf[KryoSerializer].getCanonicalName)
    val sc = SparkContext.getOrCreate(conf)
    sc
  }


  /**
   * Creates a Naive Bayes Model of Tweet and its Sentiment from the Sentiment140 file.
   *
   * @param sc            -- Spark Context.
   * @param stopWordsList -- Broadcast variable for list of stop words to be removed from the tweets.
   */
  def createAndSaveNBModel(sc: SparkContext, stopWordsList: Broadcast[List[String]]): Unit = {
    val trainDF = loadTrainingFile(sc, PropertiesLoader.SENTIMENT140_TRAIN_DATA_PATH)

    val labeled_rdd = trainDF.select("POLARITY", "STATUS").rdd.map{
      case Row(polarity: Int, tweet: String) =>
        val tweetInWords: Seq[String] = MLlibHelper.cleanTweets(tweet, stopWordsList.value)
        LabeledPoint(polarity, MLlibHelper.transformFeatures(tweetInWords))
    }
    labeled_rdd.cache()


    val naiveBayesModel: NaiveBayesModel = NaiveBayes.train(labeled_rdd, lambda = 1.0, modelType = "multinomial")
    naiveBayesModel.save(sc, PropertiesLoader.NAIVEBAYES_MODEL_PATH)
  }


  /**
   * Validates and check the accuracy of the model by comparing the polarity of a tweet from the dataset and compares it with the MLlib predicted polarity.
   *
   * @param sc            -- Spark Context.
   * @param stopWordsList -- Broadcast variable for list of stop words to be removed from the tweets.
   */
  def validateAccuracyOfNBModel(sc: SparkContext, stopWordsList: Broadcast[List[String]]): Unit = {
    val naiveBayesModel: NaiveBayesModel = NaiveBayesModel.load(sc, PropertiesLoader.NAIVEBAYES_MODEL_PATH)

    val tweetsDF: DataFrame = loadTrainingFile(sc, PropertiesLoader.SENTIMENT140_TEST_DATA_PATH)
    val actualVsPredictionRDD = tweetsDF.select("POLARITY", "STATUS").rdd.map {
      case Row(polarity: Int, tweet: String) =>
        val tweetText = replaceNewLines(tweet)
        val tweetInWords: Seq[String] = MLlibHelper.cleanTweets(tweetText, stopWordsList.value)
        (polarity.toDouble,
          naiveBayesModel.predict(MLlibHelper.transformFeatures(tweetInWords)),
          tweetText)
    }
    val accuracy = 100.0 * actualVsPredictionRDD.filter(x => x._1 == x._2).count() / tweetsDF.count()
    /*actualVsPredictionRDD.cache()
    val predictedCorrect = actualVsPredictionRDD.filter(x => x._1 == x._2).count()
    val predictedInCorrect = actualVsPredictionRDD.filter(x => x._1 != x._2).count()
    val accuracy = 100.0 * predictedCorrect.toDouble / (predictedCorrect + predictedInCorrect).toDouble*/
    println(f"""\n\t<==******** Prediction accuracy compared to actual: $accuracy%.2f%% ********==>\n""")
    saveAccuracy(sc, actualVsPredictionRDD)
  }

  /**
   * Remove new line characters.
   *
   * @param tweetText -- Complete text of a tweet.
   * @return String with new lines removed.
   */
  def replaceNewLines(tweetText: String): String = {
    tweetText.replaceAll("\n", "")
  }

  /**
   * Loads the Sentiment140 file from the specified path using SparkContext.
   *
   * @param sc                   -- Spark Context.
   * @param sentiment140FilePath -- Absolute file path of Sentiment140.
   * @return -- Spark DataFrame of the Sentiment file with the tweet text and its polarity.
   */
  def loadTrainingFile(sc: SparkContext, sentiment140FilePath: String): DataFrame = {
    val sqlContext = SQLContextSingleton.getInstance(sc)
    val trainDF: DataFrame = sqlContext.read
      .format("com.databricks.spark.csv")
      .option("header", "false")
      .option("inferSchema", "true")
      .load(sentiment140FilePath)
      .toDF("POLARITY", "ID", "DATE", "QUERY", "USER", "STATUS")

    // Drop the columns we are not interested in.
    trainDF.drop("ID").drop("DATE").drop("QUERY").drop("USER")
  }


  /**
   * Saves the accuracy computation of the ML library.
   * The columns are actual polarity as per the dataset, computed polarity with MLlib and the tweet text.
   *
   * @param sc                    -- Spark Context.
   * @param actualVsPredictionRDD -- RDD of polarity of a tweet in dataset and MLlib computed polarity.
   */
  def saveAccuracy(sc: SparkContext, actualVsPredictionRDD: RDD[(Double, Double, String)]): Unit = {
    val sqlContext = SQLContextSingleton.getInstance(sc)
    import sqlContext.implicits._
    val actualVsPredictionDF = actualVsPredictionRDD.toDF("Actual", "Predicted", "Text")
    actualVsPredictionDF.coalesce(1).write
      .format("com.databricks.spark.csv")
      .option("header", "true")
      .option("delimiter", "\t")
      // Compression codec to compress while saving to file.
      .option("codec", classOf[GzipCodec].getCanonicalName)
      .mode(SaveMode.Append)
      .save(PropertiesLoader.NAIVEBAYES_MODEL_ACCURACY_PATH)
  }

}

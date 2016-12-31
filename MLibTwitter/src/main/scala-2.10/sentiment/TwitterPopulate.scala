package sentiment
import java.text.SimpleDateFormat
import java.util.Date

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.hadoop.io.compress.GzipCodec
import org.apache.spark.SparkConf
import org.apache.spark.mllib.classification.NaiveBayesModel
import org.apache.spark.rdd.RDD
import org.apache.spark.serializer.KryoSerializer
import org.apache.spark.sql.SaveMode
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Durations, StreamingContext,Seconds}
import sentiment.corenlp.CoreNLPSentimentAnalyzer

//import sentiment.corenlp.CoreNLPSentimentAnalyzer
import sentiment.mllib.MLlibHelper
import sentiment.utils._
import redis.clients.jedis.Jedis
import twitter4j.Status
import twitter4j.auth.OAuthAuthorization
import twitter4j._
import twitter4j.conf.ConfigurationBuilder
/**
 * Created by Bhargav on 12/3/16.
 */
object TwitterPopulate {

  def main(args: Array[String]): Unit = {

    val ssc = StreamingContext.getActiveOrCreate(createSparkStreamingContext)
    val simpleDateFormat = new SimpleDateFormat("EE MMM dd HH:mm:ss ZZ yyyy")


    // Load Naive Bayes Model from the location specified in the config file.
    val naiveBayesModel = NaiveBayesModel.load(ssc.sparkContext, PropertiesLoader.NAIVEBAYES_MODEL_PATH)
    val stopWordsList = ssc.sparkContext.broadcast(StopwordsLoader.loadStopWords(PropertiesLoader.NLTK_STOPWORDS_FILE_NAME))


    /**
     * Predicts the sentiment of the tweet passed.
     * Invokes Stanford Core NLP and MLlib methods for identifying the tweet sentiment.
     *
     * @param status -- twitter4j.Status object.
     * @return tuple with Tweet ID, Tweet Text, Core NLP Polarity, MLlib Polarity, Latitude, Longitude, Profile Image URL, Tweet Date.
     */
    def Sentimentprediction(status: Status): (Long, String, String, Int, Int, Double, Double, String, String) = {
      val tweetText = replaceNewLines(status.getText)

      val (corenlpSentiment, mllibSentiment) = {
        // If tweet is in English, compute the sentiment by MLlib and also with Stanford CoreNLP.
        if (isTweetInEnglish(status)) {
          (CoreNLPSentimentAnalyzer.computeWeightedSentiment(tweetText),
            MLlibHelper.computeSentiment(tweetText, stopWordsList, naiveBayesModel))
        } else {
          // TODO: all non-English tweets are defaulted to neutral.
          // TODO: this is a workaround :: as we cant compute the sentiment of non-English tweets with our current model.
          (0, 0)
        }
      }
      (status.getId,
        status.getUser.getScreenName,
        tweetText,
        corenlpSentiment,
        mllibSentiment,
        status.getGeoLocation.getLatitude,
        status.getGeoLocation.getLongitude,
        status.getUser.getOriginalProfileImageURL,
        simpleDateFormat.format(status.getCreatedAt))

    }

    val oAuth: Some[OAuthAuthorization] = Authorization.TwitterAuth()
    val rawTweets = TwitterUtils.createStream(ssc, oAuth)  //twitter input dstreams


    //val statuses = rawTweets.map(status => status.getUser().getLocation)

    //statuses.print()
    //print(".....Class of rawTweets   ", rawTweets.getClass)
//    val hashTags = rawTweets.flatMap(status => status.getText.split(" ").filter(_.startsWith("#")))
//
//
//    val topCounts10 = hashTags.map((_, 1)).reduceByKeyAndWindow(_ + _, Seconds(10))
//      .map{case (topic, count) => (count, topic)}
//      .transform(_.sortByKey(false))
//
//    topCounts10.foreachRDD(rdd => {
//      val topList = rdd.take(5)
//      println("\nPopular topics in last 10 seconds (%s total):".format(rdd.count()))
//      topList.foreach{case (count, tag) => println("%s (%s tweets)".format(tag, count))}
//    })

    // This delimiter was chosen as the probability of this character appearing in tweets is very less.
    val DELIMITER = "¦"
    //val tweetsClassifiedPath = PropertiesLoader.tweetsClassifiedPath
    val classifiedTweets = rawTweets.filter(hasGeoLocation)
        .map(Sentimentprediction)



    classifiedTweets.foreachRDD { rdd =>
      if (rdd != null && !rdd.isEmpty() && !rdd.partitions.isEmpty) {
        //saveClassifiedTweets(rdd, tweetsClassifiedPath)

        // Now publish the data to Redis.
        rdd.foreach {
          case (id, screenName, text, sent1, sent2, lat, long, profileURL, date) => {
            val sentimentTuple = (id, screenName, text, sent1, sent2, lat, long, profileURL, date)
            // TODO -- Need to remove this and use "Spark-Redis" package for publishing to Redis.
            // TODO -- But could not figure out a way to Publish with "Spark-Redis" package though.
            // TODO -- Confirmed with the developer of "Spark-Redis" package that they have deliberately omitted the method for publishing to Redis from Spark.
            val jedis = new Jedis("localhost", 6379)
            val pipeline = jedis.pipelined
            val write = sentimentTuple.productIterator.mkString(DELIMITER)
            val p1 = pipeline.publish("TweetChannel", write)
            //println(p1.get().longValue())
            pipeline.sync()
          }
        }
      }
    }

    ssc.start()
    ssc.awaitTerminationOrTimeout(PropertiesLoader.OTAL_RUN_TIME_IN_MINUTES  * 60 * 1000)

  }

  /**
   * Checks if the tweet Status is in English language.
   * Actually uses profile's language as well as the Twitter ML predicted language to be sure that this tweet is
   * indeed English.
   *
   * @param status twitter4j Status object
   * @return Boolean status of tweet in English or not.
   */
  def isTweetInEnglish(status: Status): Boolean = {
    status.getLang == "en" && status.getUser.getLang == "en"
  }



  /**
   * Removes all new lines from the text passed.
   *
   * @param tweetText -- Complete text of a tweet.
   * @return String without new lines.
   */
  def replaceNewLines(tweetText: String): String = {
    tweetText.replaceAll("\n", "")
  }

  /**
   * Create StreamingContext.
   * Future extension: enable checkpointing to HDFS [is it really required??].
   *
   * @return StreamingContext
   */
  def createSparkStreamingContext(): StreamingContext = {
    val conf = new SparkConf()
      .setAppName(this.getClass.getSimpleName)
      // Use KryoSerializer for serializing objects as JavaSerializer is too slow.
      .set("spark.serializer", classOf[KryoSerializer].getCanonicalName)
      // For reconstructing the Web UI after the application has finished.
      .set("spark.eventLog.enabled", "false")
      // Reduce the RDD memory usage of Spark and improving GC behavior.
      .set("spark.streaming.unpersist", "true")
      .setMaster("local[2]")

    val ssc = new StreamingContext(conf, Durations.seconds(PropertiesLoader.MICRO_BATCH_TIME_IN_SECONDS))
    ssc
  }

  /**
   * Checks if the tweet Status has Geo-Coordinates.
   *
   * @param status twitter4j Status object
   * @return Boolean status of presence of Geolocation of the tweet.
   */
  def hasGeoLocation(status: Status): Boolean = {
    null != status.getGeoLocation
  }

}

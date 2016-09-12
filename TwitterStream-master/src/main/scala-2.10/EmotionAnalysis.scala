
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import twitter4j._
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.{HttpClientBuilder, DefaultHttpClient}



import scala.io.Source


object EmotionAnalysis {

  val webserver = "http://localhost:3000/post" // node server config

  var stopWords: List[String] = null
  var posWords:  List[String] = null
  var negWords:  List[String] = null
  var cityList:  List[String] = null

  // load all word list first so no need to load multiple times
  def loadWordList(): Unit = {
    stopWords = Source.fromFile("src/stop-words.txt").getLines().toList
    posWords = Source.fromFile("src/pos-words.txt").getLines().toList
    negWords = Source.fromFile("src/neg-words.txt").getLines().toList
    cityList = Source.fromFile("src/cityList.txt").getLines().toList
  }

  def stemmingProcess(text: String): String = {

    for (word <- stopWords) {
      text.replaceAll("\\b" + word + "\\b", "")
    }
    return text
  }

  def calPositiveProcess(text: String): Int = {

    var posWordsCount = 0
    for (word <- posWords ) {
      if (text.contains(word))
        posWordsCount += 1
    }

    return posWordsCount
  }

  def calNegativeProcess(text: String): Int = {

    var negWordsCount = 0
    for (word <- negWords ) {
      if (text.contains(word))
        negWordsCount += 1
    }

    return negWordsCount
  }


  def HTTPNotifier(counts: List[(Int, Int, Int, Int, Int, Int, Int, Int)]): Unit ={

    val client = new DefaultHttpClient()
    val post = new HttpPost(webserver)

    //val content = f"{/\"California': [${counts.head._1},${counts.head._2}], 'Bay Area': [${counts.head._3},${counts.head._4}], 'Los Angles': [${counts.head._5},${counts.head._6}] }"

    //val content = "{\"cal\": [%i,%i], \"BA\": [%i,%i], \"LA\": [%i,%i] }".format(counts.head._1 , counts.head._2, counts.head._3, counts.head._4, counts.head._5, counts.head._6)

    var content = "{\"California\": [%d,%d], \"San Francisco\": [%d,%d], \"Los Angles\": [%d,%d], \"San Diego\": [%d,%d] }".format(counts(0)._1 , counts(0)._2, counts(0)._3, counts(0)._4, counts(0)._5, counts(0)._6, counts(0)._7, counts(0)._8 )

    if (counts.isEmpty){
      content = "{\\\"California\\\": [0,0], \\\"San Francisco\\\": [0,0], \\\"Los Angles\\\": [0,0], \\\"San Diego\\\": [0,0] }\""
    }

    try {
      post.setEntity(new StringEntity(content))
      val response = client.execute(post)
      org.apache.http.util.EntityUtils.consume(response.getEntity)
    } catch {
      case ex: Exception => {
        val LOG = Logger.getLogger(this.getClass)
        LOG.error("exception thrown while attempting to post", ex)
      }
    }
  }


  def main(args: Array[String]) {

    loadWordList()

    val sparkConf = new SparkConf().setAppName("emotionAnalysis").setMaster("local[2]")
    val ssc = new StreamingContext(sparkConf, Seconds(2))


    //val kafkaStream = KafkaUtils.createStream(streamingContext, [ZK quorum], [consumer group id], [per-topic number of Kafka partitions to consume])
    // Create direct kafka stream with brokers and topics
    val kafkaParams = Map[String, String]("metadata.broker.list" -> "localhost:9092")
    val messages = KafkaUtils.createDirectStream[String, String, kafka.serializer.StringDecoder, kafka.serializer.StringDecoder](ssc, kafkaParams, Set("twitter-stream") )

    // 1st map: get json, 2nd map: turn into status(twitter4j) type
    // 3rd filter: filter only english tweet
    // 4th map: get tuple3 (GeoLocation[][], text, modifiedText)
    val tweets = messages.map(_._2)
      .map(messages => TwitterObjectFactory.createStatus(messages) )
      .filter(_.getLang == "en")
      .map(status => (status.getPlace.getBoundingBoxCoordinates, status.getText, status.getText.replaceAll("[^a-zA-Z\\s]", "").trim().toLowerCase()) )


    // (GeoLocation[][], postStopWordText)
    val stemmedTweets = tweets.map( tweets => (tweets._1, stemmingProcess(tweets._3)))

    // get california emotion word count (GeoLocation[][], count)
    val calPositiveTweets = stemmedTweets.map( tweets => (tweets._1, calPositiveProcess(tweets._2) ) )
    val calPositiveCounts = calPositiveTweets.map(tweets => ("group", tweets._2)) // ("calpositive", count)
      .reduceByKeyAndWindow(_ + _, Seconds(60))

    val calNegativeTweets = stemmedTweets.map( tweets => (tweets._1, calNegativeProcess(tweets._2) ) )
    val calNegativeCounts = calNegativeTweets.map(tweets => ("group", tweets._2)) // ("calnegative", count)
      .reduceByKeyAndWindow(_ + _, Seconds(60))

    // get bay area emotion word count
    val BAY_AREA_LAT_S  = 36.8
    val BAY_AREA_LONG_W = -122.75
    val BAY_AREA_LAT_N  = 37.8
    val BAY_AREA_LONG_E = -121.75
    val LA_LAT_S  = 33.7
    val LA_LONG_W = -118.67
    val LA_LAT_N  = 34.304952
    val LA_LONG_E = -117.684889
    val SD_LAT_S  = 32.553172
    val SD_LONG_W = -117.329940
    val SD_LAT_N  = 32.955272
    val SD_LONG_E = -116.897580

    // only count left
    val bayAreaPosCounts = calPositiveTweets.filter( tweets => tweets._1(0)(0).getLatitude > BAY_AREA_LAT_S )
      .filter( tweets => tweets._1(0)(0).getLatitude < BAY_AREA_LAT_N )
      .filter( tweets => tweets._1(0)(1).getLongitude > BAY_AREA_LONG_W )
      .filter( tweets => tweets._1(0)(1).getLongitude < BAY_AREA_LONG_E )
      .map(tweets => ("group", tweets._2))
      .reduceByKeyAndWindow(_ + _, Seconds(60))

    //    println("BA pos:")
    //    bayAreaPosCounts.print()

    // only count left
    val bayAreaNegCounts = calNegativeTweets.filter( tweets => tweets._1(0)(0).getLatitude > BAY_AREA_LAT_S )
      .filter( tweets => tweets._1(0)(0).getLatitude < BAY_AREA_LAT_N )
      .filter( tweets => tweets._1(0)(1).getLongitude > BAY_AREA_LONG_W )
      .filter( tweets => tweets._1(0)(1).getLongitude < BAY_AREA_LONG_E )
      .map(tweets => ("group", tweets._2))
      .reduceByKeyAndWindow(_ + _, Seconds(60))

    //    println("BA neg:")
    //    bayAreaNegCounts.print()

    val losAnglesPosCounts = calPositiveTweets.filter( tweets => tweets._1(0)(0).getLatitude > LA_LAT_S )
      .filter( tweets => tweets._1(0)(0).getLatitude < LA_LAT_N )
      .filter( tweets => tweets._1(0)(1).getLongitude > LA_LONG_W )
      .filter( tweets => tweets._1(0)(1).getLongitude < LA_LONG_E )
      .map(tweets => ("group", tweets._2))
      .reduceByKeyAndWindow(_ + _, Seconds(60))

    //    println("LA pos")
    //    losAnglesPosCounts.print()

    val losAnglesNegCounts = calNegativeTweets.filter( tweets => tweets._1(0)(0).getLatitude > LA_LAT_S )
      .filter( tweets => tweets._1(0)(0).getLatitude < LA_LAT_N )
      .filter( tweets => tweets._1(0)(1).getLongitude > LA_LONG_W )
      .filter( tweets => tweets._1(0)(1).getLongitude < LA_LONG_E )
      .map(tweets => ("group", tweets._2))
      .reduceByKeyAndWindow(_ + _, Seconds(60))

    //    println("LA neg")
    //    losAnglesNegCounts.print()

    val sanDiegoPosCounts = calPositiveTweets.filter( tweets => tweets._1(0)(0).getLatitude > SD_LAT_S )
      .filter( tweets => tweets._1(0)(0).getLatitude < SD_LAT_N )
      .filter( tweets => tweets._1(0)(1).getLongitude > SD_LONG_W )
      .filter( tweets => tweets._1(0)(1).getLongitude < SD_LONG_E )
      .map(tweets => ("group", tweets._2))
      .reduceByKeyAndWindow(_ + _, Seconds(60))

    //    println("LA pos")
    //    losAnglesPosCounts.print()

    val sanDiegoNegCounts = calNegativeTweets.filter( tweets => tweets._1(0)(0).getLatitude > SD_LAT_S )
      .filter( tweets => tweets._1(0)(0).getLatitude < SD_LAT_N )
      .filter( tweets => tweets._1(0)(1).getLongitude > SD_LONG_W )
      .filter( tweets => tweets._1(0)(1).getLongitude < SD_LONG_E )
      .map(tweets => ("group", tweets._2))
      .reduceByKeyAndWindow(_ + _, Seconds(60))

    val result = calPositiveCounts.join(calNegativeCounts)
      .join(bayAreaPosCounts.join(bayAreaNegCounts) )
      .join(losAnglesPosCounts.join(losAnglesNegCounts) )
      .join(sanDiegoPosCounts.join(sanDiegoNegCounts) )
      .map(counts => (counts._2._1._1._1._1, counts._2._1._1._1._2, counts._2._1._1._2._1, counts._2._1._1._2._2, counts._2._1._2._1, counts._2._1._2._2, counts._2._2._1, counts._2._2._2))

    //    result.print()

    result.foreachRDD(rdd => {

      println("\nresults in last 60 seconds (%s total):".format(rdd.count()))
      rdd.foreach(rdd => println(rdd))
    })

    result.foreachRDD(rdd => {
      HTTPNotifier( rdd.collect().toList)
    })
    //    result.foreachRDD(HTTPNotifier(result))

    // Start the computation
    ssc.start()
    ssc.awaitTermination()
  }

}

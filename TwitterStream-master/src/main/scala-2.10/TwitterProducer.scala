
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import twitter4j._
import twitter4j.conf.ConfigurationBuilder

object TwitterProducer {



  def main(args: Array[String]) {

    if (args.length < 4) {
      System.err.println("Usage: TwitterPopularTags <consumer key> <consumer secret> " +
        "<access token> <access token secret> [<filters>]")
      System.exit(1)
    }

    val Array(consumerKey, consumerSecret, accessToken, accessTokenSecret) = args.take(4)
    val filters = args.takeRight(args.length - 4)

    // Set the system properties so that Twitter4j library used by twitter stream
    // can use them to generate OAuth credentials
    val cb = new ConfigurationBuilder()
    cb.setOAuthConsumerKey(consumerKey)
    cb.setOAuthConsumerSecret(consumerSecret)
    cb.setOAuthAccessToken(accessToken)
    cb.setOAuthAccessTokenSecret(accessTokenSecret)
    cb.setJSONStoreEnabled(true)
    cb.setIncludeEntitiesEnabled(true)

    System.setProperty("twitter4j.oauth.consumerKey", consumerKey)
    System.setProperty("twitter4j.oauth.consumerSecret", consumerSecret)
    System.setProperty("twitter4j.oauth.accessToken", accessToken)
    System.setProperty("twitter4j.oauth.accessTokenSecret", accessTokenSecret)

    // Kafka Setup

    /** Producer properties **/
    val props = new java.util.Properties()
    props.put("metadata.broker.list", "localhost:9092" )
    props.put("serializer.class", "kafka.serializer.StringEncoder" )
    props.put("request.required.acks", "1" )
    props.put("kafka.topic", "twitter-stream")
    props.put("producer.type", "sync")
    props.put("bootstrap.servers", "localhost:9092")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    // val config = new ProducerConfig(props)
    val producer = new KafkaProducer[String,String](props)


    val twitterStream = new TwitterStreamFactory(cb.build()).getInstance()

    val shouldPrintTweetsOnScreen = true
    val shouldSendTweetsToKafka   = true

    // kafka listener
    val listener = new StatusListener() {

      def onStatus(status: Status) {
        if (shouldPrintTweetsOnScreen) {
          println(status.getUser.getScreenName + ": " + status.getText + "\n")
        }
        if (shouldSendTweetsToKafka) {
          val data = new ProducerRecord[String, String]("twitter-stream", TwitterObjectFactory.getRawJSON(status))
          producer.send(data)
        }
      }

      def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {
      }

      def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {
      }

      def onScrubGeo(userId: Long, upToStatusId: Long) {
      }

      def onException(ex: Exception) {
        println("EXCEPTION:" + ex)
        twitterStream.shutdown()
      }

      def onStallWarning(warning: StallWarning) {
      }
    }


    // add location filter, this will cover california  and most nevada
    val fq = new FilterQuery()
    fq.locations( Array(-124.482003, 32.528832), Array(-114.131211, 42.009517))

    // start stream
    twitterStream.addListener(listener)
    twitterStream.filter(fq)

  }
}

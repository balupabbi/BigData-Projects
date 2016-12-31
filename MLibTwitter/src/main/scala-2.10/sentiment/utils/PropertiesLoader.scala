package sentiment.utils

/**
 * Contains all config information
 */

import com.typesafe.config.{Config, ConfigFactory}


object PropertiesLoader {
  private val conf: Config = ConfigFactory.load("application.conf")

  val SENTIMENT140_TRAIN_DATA_PATH = conf.getString("SENTIMENT140_TRAIN_DATA_ABSOLUTE_PATH")
  val SENTIMENT140_TEST_DATA_PATH = conf.getString("SENTIMENT140_TEST_DATA_ABSOLUTE_PATH")
  val NLTK_STOPWORDS_FILE_NAME  = conf.getString("NLTK_STOPWORDS_FILE_NAME ")

  val NAIVEBAYES_MODEL_PATH = conf.getString("NAIVEBAYES_MODEL_ABSOLUTE_PATH")
  val NAIVEBAYES_MODEL_ACCURACY_PATH = conf.getString("NAIVEBAYES_MODEL_ACCURACY_ABSOLUTE_PATH ")

  val TWEETS_RAW_PATH = conf.getString("TWEETS_RAW_ABSOLUTE_PATH")
  val SAVE_RAW_TWEETS = conf.getBoolean("SAVE_RAW_TWEETS")

  val TWEETS_CLASSIFIED_PATH = conf.getString("TWEETS_CLASSIFIED_ABSOLUTE_PATH")

  val CONSUMER_KEY = conf.getString("CONSUMER_KEY")
  val CONSUMER_SECRET = conf.getString("CONSUMER_SECRET")
  val ACCESS_TOKEN_KEY = conf.getString("ACCESS_TOKEN_KEY")
  val ACCESS_TOKEN_SECRET = conf.getString("ACCESS_TOKEN_SECRET")

  val MICRO_BATCH_TIME_IN_SECONDS = conf.getInt("STREAMING_MICRO_BATCH_TIME_IN_SECONDS")
  val OTAL_RUN_TIME_IN_MINUTES = conf.getInt("TOTAL_RUN_TIME_IN_MINUTES")

}

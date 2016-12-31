package sentiment.utils

import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.ConfigurationBuilder

/**
 * Created by Bhargav on 12/7/16.
 */
object Authorization {

  def TwitterAuth(): Some[OAuthAuthorization] = {
    System.setProperty("twitter4j.oauth.consumerKey", PropertiesLoader.CONSUMER_KEY)
    System.setProperty("twitter4j.oauth.consumerSecret", PropertiesLoader.CONSUMER_SECRET)
    System.setProperty("twitter4j.oauth.accessToken", PropertiesLoader.ACCESS_TOKEN_KEY)
    System.setProperty("twitter4j.oauth.accessTokenSecret", PropertiesLoader.ACCESS_TOKEN_SECRET)

    val cb = new ConfigurationBuilder()
//    cb.setOAuthConsumerKey(PropertiesLoader.CONSUMER_KEY)
//    cb.setOAuthConsumerSecret(PropertiesLoader.)
//    cb.setOAuthAccessToken(accessToken)
//    cb.setOAuthAccessTokenSecret(accessTokenSecret)
//    cb.setJSONStoreEnabled(true)
//    cb.setIncludeEntitiesEnabled(true)
    val oAuth = Some(new OAuthAuthorization(cb.build()))


    oAuth

  }

}

package sentiment.Rough_Draft

import org.apache.spark.serializer.KryoSerializer
import org.apache.spark.{SparkConf, SparkContext}
import redis.clients.jedis.Jedis

/**
 * Created by Bhargav on 12/8/16.
 */
object Demo2 {

  def main(args: Array[String]): Unit = {
    val test = "hello my name is twitter"
//    val jedis = new Jedis("localhost", 6379)
//    print("Connection Successful")
//
//    print("Pushing", " ", jedis.lpush("t1", test))
//    print("Popping", " ", jedis.lpop(test))

    //print("Getting something", " ", jedis.)
    val sc = createSparkContext()



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

}

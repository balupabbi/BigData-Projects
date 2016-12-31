package sentiment.Rough_Draft

import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.serializer.KryoSerializer

/**
  * Created by Bhargav on 12/10/16.
  */
object Wordcount {


  def main(args: Array[String]): Unit = {

    val conf = new SparkConf()
      .setAppName(this.getClass.getSimpleName)
      .setMaster("local[2]")
      .set("spark.serializer", classOf[KryoSerializer].getCanonicalName)

    val sc = SparkContext.getOrCreate(conf)
    val input = sc.textFile("src/main/scala-2.10/sentiment/Rough_Draft/readsamp.txt")

    val firstrdd = input.flatMap(line ⇒ line.split(" "))
    firstrdd.collect.foreach(println)
    println(".................................")
    val secondrdd = firstrdd.map(word => (word, 1))
    secondrdd.collect.foreach(println)
    println(".................................")
    val thirdrdd = secondrdd.reduceByKey(_ + _)
    thirdrdd.collect.foreach(println)

//    val count = input.flatMap(line ⇒ line.split(" "))
//      .map(word ⇒ (word, 1))
//      .reduceByKey(_ + _)

//    count.collect.foreach(println)



  }



}

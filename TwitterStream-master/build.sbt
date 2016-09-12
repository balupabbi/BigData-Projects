name := "TwitterStream"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.apache.spark" %  "spark-core_2.10" % "1.4.1",
  "org.apache.spark" %  "spark-streaming_2.10" % "1.4.1",
  "org.apache.spark" %  "spark-streaming-twitter_2.10" % "1.4.1",
  "org.twitter4j"    %  "twitter4j-core" % "4.0.4",
  "org.twitter4j"    %  "twitter4j-stream" % "4.0.4",
  "org.apache.kafka" %  "kafka_2.10" % "0.8.2.1",
  "org.apache.spark" %  "spark-streaming-kafka_2.10" % "1.4.1",
  "org.apache.httpcomponents" % "httpclient" % "4.3.6",
  "org.apache.httpcomponents" % "httpcore" % "4.4.1"
)
    
# EMOTION ANALYSIS #

Please follow the following steps to run the application

### Kafka server setup ###
* Step 1: Download the code
[Download](https://www.apache.org/dyn/closer.cgi?path=/kafka/0.8.2.0/kafka_2.10-0.8.2.0.tgz) the 0.8.2.0 release and un-tar it.
```sh
> tar -xzf kafka_2.10-0.8.2.0.tgz
> cd kafka_2.10-0.8.2.0
```
* Step 2: Start the ZooKeeper and Kafka server
``` sh
> bin/zookeeper-server-start.sh config/zookeeper.properties
> bin/kafka-server-start.sh config/server.properties
```
* Step 3: Create a topic
``` sh
> bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic twitter-stream
```

### NodeJS Setup ###

This application will send an POST request to an NodeJS server with URL ``` http://localhost:3000/post ```
Download or clone [EmotionAnalysisNodeJs][1] server and run
```sh 
npm install 
node src/index.js
```



### Run Application ###

0. Download [Intellij][2] and open run it
1. Clone the project to local and import into Intellij
2. Let SBT download the dependencies
3. Run TwitterProducer object, should see application running and twitter feeds coming in.
4. Run EmotionAnalysis object, should see application start running.
5. Open browser and go to ``` localhost:3000 ```
6. Should see information coming up


### Other ###

There are also other files in the folder that is not in the project but 
is also runnable. 



[1] https://github.com/CocoHot/EmotionAnalysisNodeJS
[2] https://www.jetbrains.com/idea/download/
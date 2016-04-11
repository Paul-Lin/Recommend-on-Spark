package com.moon.app

import com.google.gson.GsonBuilder
import kafka.serializer.StringDecoder
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{StreamingContext, Seconds}
import net.sf.json._
import redis.clients.jedis.JedisPool

/**
  * Created by lin on 4/10/16.
  */
object UserClickCountAnalytics {
  def main(args:Array[String]): Unit ={
    // Create a StreamingContext with the given master URL
    val conf=new SparkConf().setAppName("UserClickCountStat")
    val ssc=new StreamingContext(conf,Seconds(5))

    // Kafka configurations
    val topics=Set("user_events")
    val brokers="localhost:9092"
    val kafkaParams=Map[String,String](
      "metadata.broker.list" -> brokers,
      "serializer.class" -> "kafka.serializer.StringEncoder"
    )

    val dbIndex=1
    val clickHashKey="app::users::click"

    // Create a direct stream
    val kafkaStream=KafkaUtils.createDirectStream[String,String,StringDecoder,StringDecoder](ssc,kafkaParams,topics)

    val events=kafkaStream.flatMap(line => {
        val data=JSONObject.fromObject(line._2)
        Some(data)
    })
    // Compute user click times
    val userClicks=events.map(x => (x.getString("uid"),x.getInt("click_count"))).reduceByKey(_ + _)

    userClicks.foreachRDD(rdd => {
      rdd.foreachPartition(partitionOfRecords => {
        partitionOfRecords.foreach(pair =>{
          val uid=pair._1
          val clickCount=pair._2
          val jedis=RedisClient.pool.getResource
          jedis.select(dbIndex)
          jedis.hincrBy(clickHashKey,uid,clickCount)
          RedisClient.pool.returnResource(jedis)
        })
      })
    })
    ssc.start()
    ssc.awaitTermination()
  }
}
object RedisClient extends Serializable{
  val redisHost="localhost"
  val redisPort=6379
  val redisTimeout=30000
  lazy val pool=new JedisPool(new GenericObjectPoolConfig(),redisHost,redisPort,redisTimeout)

  lazy val hook=new Thread{
    override def run={
      println("Execute hook thread: "+this)
      pool.destroy()
    }
  }
  sys.addShutdownHook(hook.run)
}

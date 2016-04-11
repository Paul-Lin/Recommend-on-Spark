package com.moon.app

import org.apache.spark.SparkConf
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{StreamingContext, Seconds}

/**
  * Created by lin on 4/10/16.
  */
object NetworkWordCount {
  def main(args:Array[String]): Unit ={
    // Create the context with a 1 second batch size
    val sparkConf=new SparkConf().setAppName("NetWorkWordCount")
    val ssc=new StreamingContext(sparkConf,Seconds(1))

    // Create a socket stream on target ip:port and count the
    // words in input stream of \n delimited text (eg. generated by 'nc')
    // Note that no duplication in storage level only for running locally
    // Replication necessary in distributed scenario for fault tolerance
    val lines=ssc.socketTextStream("localhost","9999".toInt,StorageLevel.MEMORY_AND_DISK_SER)
    val words=lines.flatMap(_.split(" "))
    val wordCounts=words.map(x => (x,1)).reduceByKey(_ + _)
    wordCounts.print()

    ssc.start()
    ssc.awaitTermination()
  }
}
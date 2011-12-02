package com.awkin.suedespider

import java.net.URL

import scala.actors._
import Actor._
import scala.io.Source
import scala.xml._
import com.mongodb.casbah.Imports._

object Main {
    def main(args: Array[String]) = {
        // read configuration
        Config.readConf()
        println("numOfJobPerThread = %d".format(Config.numOfJobPerThread))

        // mongodb settings
        val mongoConn = MongoConnection()

        val feeds = Source.fromFile("config/data/feeds.txt")
                                .getLines.foldLeft(Set[String]()) { 
                                    _ ++ Set(_) 
                                }
        val feedSize = feeds.count(feed => !isComment(feed))
        val numOfSpider = 
            if (feedSize % Config.numOfJobPerThread > 0) 
                feedSize / Config.numOfJobPerThread + 1
            else
                feedSize / Config.numOfJobPerThread

        // Spider threads
        val caller = self
        val (feedSubset, _) = feeds.foldLeft((Set[String](), 0)) { (res, feed) => 
            val (feedSubset, count) = res
            if (count < Config.numOfJobPerThread) {
                feed match {
                    //if the url is commented
                    case isComment() => (feedSubset, count)
                    case _ => (feedSubset++Set(feed), count+1)
                }
            } else {
                (new Spider(feedSubset, mongoConn, caller)).start()
                feed match {
                    //if the url is commented
                    case isComment() => (Set[String](), 0)
                    case _ => (Set(feed), 1)
                }
            }
        }
        //feeds left
        if (!feedSubset.isEmpty) {
            (new Spider(feedSubset, mongoConn, caller)).start()
        }

        waitForSpider(numOfSpider)
        mongoConn.close()
    }

    def waitForSpider(numOfSpider: Int) {
        if (numOfSpider > 0) {
            receiveWithin(Config.maxSpiderWaitTime) {
                case (_, job: Actor) => 
                    println("Job" + job + " done")
                    waitForSpider(numOfSpider-1)
                case TIMEOUT =>
                    println("Job TIMEOUT!!")
                    waitForSpider(0)
            }
        }
    }
}

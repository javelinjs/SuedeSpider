package com.awkin.suedespider

import java.net.URL

import scala.actors._
import Actor._
import scala.io.Source
import scala.xml._

object Main {
    def main(args: Array[String]) = {
        // read configuration
        Config.readConf()
        println("JobThreadNum = %d".format(Config.jobThreadNum))

        /* read the feeds */
        val feeds = 
            (Set[String]() /: Source.fromFile(Config.feedFile).getLines) { (set, url) =>
                url match {
                    case isComment() => set
                    case notComment() => set ++ Set(url)
                }
            }

        if (feeds.count(_=>true) == 0) {
            throw (new EmptyFeedSetException)
        }

        val spiderCageVector = 
            for (i <- 1 to Config.jobThreadNum) yield (new Spider().start())

        val alarmService = new Alarm(feeds, spiderCageVector.toList)
        alarmService.start()
        Thread sleep 10000
    }
}

class EmptyFeedSetException extends Exception {}

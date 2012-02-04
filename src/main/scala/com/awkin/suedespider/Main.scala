package com.awkin.suedespider

import java.net.URL

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter

import scala.actors._
import Actor._
import scala.io.Source
import scala.io.Codec
import scala.xml._

object Main {
    def main(args: Array[String]) {
        implicit val codec = Codec("UTF-8")
        val logger = LoggerFactory.getLogger("Main")
        /* first arg is the conf file path */
        val confFile =
            if (args.length > 0) {
                Some(args(0))
            } else {
                None
            }
        // read configuration
        Config.readConf(confFile)
        logger.info("JobThreadNum = %d".format(Config.jobThreadNum))

        /* read the feeds */
        val feeds = 
        try {
            (Set[String]() /: Source.fromFile(Config.feedFile).getLines) { (set, url) =>
                url match {
                    case isComment() => set
                    case notComment() => set ++ Set(url)
                }
            }
         } catch {
            case ex => 
                logger.error(ex.getMessage())
                Set[String]()
         }

        val feedCount = feeds.count(_=>true)
        logger.info("{} feed(s) to crawler", feedCount)
        if (feedCount == 0) {
            logger.error("fail to read from {}", Config.feedFile)
            return
        }

        val spiderCageVector = 
            for (i <- 1 to Config.jobThreadNum) yield (new Spider().start())

        val alarmService = new Alarm(feeds, spiderCageVector.toList)
        alarmService.start()
        Thread sleep 10000
    }
}

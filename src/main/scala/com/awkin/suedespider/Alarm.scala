package com.awkin.suedespider

import java.util.Date

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter

import scala.actors._
import Actor._

class Alarm(val feeds: Set[String], val spiderCage: List[Actor]) extends Actor {

    val logger: Logger = LoggerFactory.getLogger(classOf[Alarm])

    val feedList: List[FeedAlarmData] = feedListInit

    def act() {
        var remainAlarmNum = 0
        var loop = true
        var firstReach = true
        while (loop) {
            logger.info("Clean the remain {} spider(s)", remainAlarmNum)
            //clean the spider remains
            remainAlarmNum = 
                waitForSpider(remainAlarmNum, 1000)

            val alarmNum = alarm(firstReach)
            firstReach = false

            remainAlarmNum += 
                waitForSpider(alarmNum, Config.maxSpiderWaitTime*1000)
            
            loop = 
                if (Config.runAsDaemon) {
                    Thread sleep (Config.alarmCheckInterval*1000)
                    true
                } else {
                    /* end */
                    logger.info("going to send quit to spiders")
                    waitForSpider(remainAlarmNum, 10000)
                    logger.info("send quit to spiders")
                    callToQuit()
                    Thread sleep 10000
                    logger.info("ready to quit")
                    false
                }
        }
    }

    private def callToQuit() = {
        val quitNum = spiderCage.length
        for (i <- 0 until quitNum) {
            spiderCage(i) ! (this, "quit")
        }
        quitNum
    }

    private def alarm(firstReach: Boolean = false) = {
        val (idx, cageNumber, alarmNum) = 
        ((0, 0, 0) /: feedList) { (idx_cageNumber_alarmNum, feed) =>
            val (idx, cageNumber, alarmNum) = idx_cageNumber_alarmNum

            val now = new Date()
            val lastBuild = feed.lastBuildDate
            val secondspan = (now.getTime - lastBuild.getTime) / 1000

            /* check for the buildFreq of the current feed */
            if (firstReach || secondspan > feed.buildFreq) {
                /* send the url to spider */ 
                logger.info("Send {} to spider {}", feed.url, cageNumber)
                spiderCage(cageNumber) ! (this, feed.url, idx)

                (idx+1, (cageNumber+1)%spiderCage.length, alarmNum+1)
            } else {
                (idx+1, cageNumber, alarmNum)
            }
        }
        alarmNum
    }

    /* return how many spider need to wait */
    private def waitForSpider(numOfSpider: Int, timeout: Int): Int = {
        if (numOfSpider > 0) {
            logger.info("Wait for {} spider(s)", numOfSpider)
            //receive {
            receiveWithin(timeout) {
                case (feedIdx:Int, update:Boolean, lastBuild:Date) =>
                    if (feedIdx < feedList.length) {
                        logger.info("receive response")
                        val historybuild = feedList(feedIdx).lastBuildDate
                        val feedFreqSec = feedList(feedIdx).buildFreq
                        val buildFreqSec: Long = 
                            if (update) {
                                if (lastBuild after historybuild) {
                                    (lastBuild.getTime - historybuild.getTime) / 1000
                                } else {
                                    feedFreqSec
                                }
                            } else {
                                if (feedFreqSec > Config.alarmCheckIntervalMax) {
                                    /* No more penalty */
                                    feedFreqSec
                                } else {
                                    feedFreqSec * 2
                                }
                            }
                        /* update the build frequency */
                        feedList(feedIdx).buildFreq_=(buildFreqSec)
                        feedList(feedIdx).lastBuildDate_=(lastBuild)
                        logger.info("%s buildFreqSec = %d, lastBuildDate = %s".format( 
                                        feedList(feedIdx).url, 
                                        feedList(feedIdx).buildFreq,
                                        feedList(feedIdx).lastBuildDate.toString))
                    } else {
                        logger.warn("idx expend the feedList")
                    }
                    waitForSpider(numOfSpider-1, timeout)
                case TIMEOUT =>
                    logger.warn("job TIMEOUT")
                    numOfSpider
                case "quit" =>
                    waitForSpider(numOfSpider-1, timeout)
                case _ =>
                    logger.warn("received Something Illegal")
                    numOfSpider
            }
        } else {
            logger.info("no spider to wait")
            0
        }
    }

    /* init the feed list according to the Set passed by Main */
    private def feedListInit = {
        (List[FeedAlarmData]() /: feeds) { (list, feed: String) => 
            val alarmData = new FeedAlarmData(feed, Config.alarmCheckFreqInit, new Date())
            alarmData :: list 
        }
    }
}

/* buildFreq: seconds */
class FeedAlarmData( var url: String,
                        var buildFreq: Long, 
                        var lastBuildDate: Date) { 
}

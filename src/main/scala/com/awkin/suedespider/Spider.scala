package com.awkin.suedespider

import java.net.URL
import java.util.Date
import java.io._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter

import scala.io.Source
import scala.xml._
import scala.actors._
import Actor._

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoDB

/* FIXME: trait DaemonActor extends Actor */
class Spider extends Actor {

    val logger: Logger = LoggerFactory.getLogger(classOf[Spider])

    def act() {
        //Thread sleep 4000
        while (true) {
            receive {
                case (caller : Actor, url : String, idx : Int) =>
                    logger.info("ready to crawl {}", url)

                    val (updated: Boolean, lastBuildDate: Date) = crawlRss(url)
                    /* reply to the Alarm */
                    caller ! (idx, updated, lastBuildDate)
                case (caller : Actor, "quit") =>
                    logger.info("ready to quit")
                    exit
                case _ => 
                    logger.warn("spider receive invalid msg")
            }
        }
    }

    /* return whether the url is updated 
        and the last_build_date of the feed
    */
    private def crawlRss(url: String): (Boolean, Date) = {
        try {
            logger.info("try to crawl from {}", url)

            val mongoConn = MongoConnection(Config.dbHost, Config.dbPort)
            val mongoDB = mongoConn(Config.db)
            /* Auth */
            mongoDB.authenticate(Config.dbUser, Config.dbPwd)

            val channelColl = mongoDB("channel")
            val itemColl = mongoDB("item")
            val featureColl = mongoDB("feature")

            val rss = 
                url match {
                    case crawlOriginalUrl() => 
                        val xml = XML.load(new URL(crawlOriginalUrl(url)))
                        new Rss(xml, crawlOriginalUrl(url), true)
                    case _ => 
                        val xml = XML.load(new URL(url))
                        new Rss(xml, url, false)
                }

            //channel related
            val channelObj = MongoDBObject("link"->rss.channel_link)
            val channelInsHistory = channelColl.findOne(channelObj, 
                                        MongoDBObject("lastBuildDate"->1))

            val (channelInsNew: DBObject, historyBuildDate: Date,
                    rssChannelDoNotContainsLastBuildDate: Boolean) = 
                if (channelInsHistory == None) {
                    //new channel
                    // if no lastBuildDate in rss, then set to current time
                    val lastBuild: Date = 
                        rss.lastBuildDate.getOrElse(new Date())
                    val ins = channelColl.findAndModify(channelObj, 
                                MongoDBObject("lastBuildDate"->1),
                                MongoDBObject(), false,
                                $set("title"->rss.channel_title,
                                        "link"->rss.channel_link,
                                        "desc"->rss.channel_desc,
                                        "lastBuildDate"->lastBuild,
                                        "buildFreq"->rss.buildFreq), 
                                true, true)
                    (ins.get, new Date(0), false)
                } else {
                    val historyBuild: Date = 
                        channelInsHistory.get("lastBuildDate").asInstanceOf[Date]
                    // if no lastBuildDate in rss, then don't change it
                    val lastBuild: Date = 
                        rss.lastBuildDate.getOrElse(historyBuild)

                    val ins = channelColl.findAndModify(channelObj, 
                                MongoDBObject("lastBuildDate"->1),
                                MongoDBObject(), false,
                                $set("title"->rss.channel_title,
                                        "desc"->rss.channel_desc,
                                        "lastBuildDate"->lastBuild,
                                        "buildFreq"->rss.buildFreq), 
                                true, true)
                    val noDate = 
                        if (historyBuild equals lastBuild) true
                        else false
                    (ins.get, historyBuild, noDate)
                }

            //items related
            val items = rss.items
            val updated: Boolean = 
            (false /: items) { (alreadyUpdated: Boolean, item: RssItem) => 
                //check whether the item is already in db
                val (needSave, emptyDate) = 
                        needSave_emptyDate(item, historyBuildDate, mongoConn)
                if (needSave) {
                    val pubDate: Date = 
                        if (emptyDate) {
                            // use current time
                            new Date()
                        } else {
                            item.pubDate.get
                        }
                    //add new item
                    val itemObj = 
                            MongoDBObject(
                                "channel"->channelInsNew.get("_id"),
                                "title"->item.title,
                                "desc"->item.desc,
                                "content"->item.content,
                                "link"->item.link,
                                "pubDate"->pubDate)
                    itemColl += itemObj
                    //item features
                    featureColl += FeatureExtractor(itemObj._id, item)
                }

                if (!alreadyUpdated && needSave) true else alreadyUpdated
            }

            val channelLastBuildDate: Date = 
            if (updated) {
                logger.info("{} changed", url)
                /* rss do not contain the "lastBuildDate" setting,
                   we update the channel's lastBuildDate to current time */
                val lastBuildDate = 
                    channelInsNew.get("lastBuildDate").asInstanceOf[Date]

                val updatedDate = 
                    if (rssChannelDoNotContainsLastBuildDate) {
                        val newDate = new Date()
                        channelColl.update(
                            MongoDBObject("_id"->channelInsNew.get("_id")), 
                                            $set("lastBuildDate"->newDate))
                        newDate
                    } else {
                        lastBuildDate
                    }
                updatedDate
            } else {
                historyBuildDate
            }

            /* return whether this url has been updated */
            (updated, channelLastBuildDate)
            //XML save (rss.channel_title + ".xml", xml, "UTF-8")
        } catch {
            case ex => 
                logger.error(ex.getMessage)
                val content = "fail to crawl from %s".format(url)
                logger.warn(content)
                //record the failure
                val writer = new FileWriter(Config.failOutFile, true)
                writer write (content + "\n")
                writer.close
                (false, new Date(0))
        }
    }

    /* Only the pubDate after the lastBuildDate of channel in db 
        orelse pubDate is empty, the link not exists in db
        orelse pubDate,link is empty, the title not exists in db
        will be crawled */
    private def needSave_emptyDate(item: RssItem, lastBuildDate: Date, 
                                    conn: MongoConnection): 
                                (Boolean, Boolean) = {

        val emptyDate: Boolean = (item.pubDate == None)
        val newItem: Boolean = item.pubDate.getOrElse(new Date(0)) after lastBuildDate
        // connection
        val channelColl = conn(Config.db)("channel")
        val itemColl = conn(Config.db)("item")

        val needSave: Boolean =
        newItem match {
            case false =>
                emptyDate match {
                    case true =>
                        /* how many days before should the duplicated items checked */
                        val day = new Date()
                        val nDayBefore = (day.getTime()/1000)-60*60*24*Config.dupItemCheckDay
                        day setTime nDayBefore*1000

                        val found = 
                            if (item.link.length > 0) {
                                val cond = MongoDBObject("link"->item.link) ++ 
                                                ("pubDate" $gt day)
                                itemColl.findOne(cond, MongoDBObject("_id"->1))
                            } else if (item.title.length > 0) {
                                val cond = MongoDBObject("title"->item.title) ++
                                                ("pubDate" $gt day)
                                itemColl.findOne(cond, MongoDBObject("_id"->1))
                            } else {
                                Option()
                            }
                        found match {
                            case None => true
                            case _ => false
                        }
                    case false => false
                }
            case true => true
        }
        (needSave, emptyDate)
    }
}

object crawlOriginalUrl {
    def apply(url : String) : String = url.substring(1, url.length)
    def unapply(url : String) : Boolean = (url(0) == '+')
}


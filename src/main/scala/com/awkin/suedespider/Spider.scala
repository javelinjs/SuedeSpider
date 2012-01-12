package com.awkin.suedespider

import java.net.URL
import java.util.Date
import java.io._

import scala.io.Source
import scala.xml._
import scala.actors._
import Actor._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoConnection

class Spider extends Actor {

    def act() {
        //Thread sleep 4000
        while (true) {
            receive {
                case (caller : Actor, url : String, idx : Int) =>
                    println("[Spider] Ready to crawl " + url)
                    val (updated: Boolean, lastBuildDate: Date) = crawlRss(url)
                    /* reply to the Alarm */
                    caller ! (idx, updated, lastBuildDate)
                case _ => println("[Spider] WARING: Spider receive invalid msg")
            }
        }
    }

    /* return whether the url is updated 
        and the last_build_date of the feed
    */
    private def crawlRss(url: String): (Boolean, Date) = {
        try {
            println("[Spider] Try to crawl from %s".format(url))

            val mongoConn = MongoConnection()
            val channelColl = mongoConn(Config.db)("channel")
            val itemColl = mongoConn(Config.db)("item")

            val xml = XML.load(new URL(url))
            val rss = new Rss(xml)

            //channel related
            val channelObj = MongoDBObject("link"->rss.channel_link)
            val channelInsLast = channelColl.findOne(channelObj, 
                                        MongoDBObject("lastBuildDate"->1))
            val lastBuildDate = 
                channelInsLast.get("lastBuildDate").asInstanceOf[Date]
            // update the channel
            val channelInsNew = channelColl.findAndModify(channelObj, 
                                MongoDBObject("lastBuildDate"->1),
                                MongoDBObject(), false,
                                $set("title"->rss.channel_title,
                                        "desc"->rss.channel_desc,
                                        "lastBuildDate"->rss.lastBuildDate,
                                        "buildFreq"->rss.buildFreq), 
                                true, true)

            //items related
            val items = rss.items
            val updated: Boolean = 
            (false /: items) { (alreadyUpdated: Boolean, item: RssItem) => 
                //check whether the item is already in db
                val (needSave, emptyDate) = 
                        needSave_emptyDate(item, lastBuildDate, mongoConn)
                if (needSave) {
                    val pubDate: Date = 
                        if (emptyDate) {
                            channelInsNew.get("lastBuildDate").asInstanceOf[Date]
                        } else {
                            item.pubDate
                        }
                    val itemObj = 
                            MongoDBObject(
                                "channel"->channelInsNew.get("_id"),
                                "title"->item.title,
                                "desc"->item.desc,
                                "content"->item.content,
                                "link"->item.link,
                                "pubDate"->pubDate)
                    itemColl += itemObj
                }

                if (!alreadyUpdated && needSave) true else alreadyUpdated
            }

            if (updated) println("[Spider]" + url + " changed")
            /* return whether this url has been updated */
            (updated, lastBuildDate)
            //XML save (rss.channel_title + ".xml", xml, "UTF-8")
        } catch {
            case _ => 
                val content = "[Spider] Fail to crawl from %s".format(url)
                println(content)
                /* record the failure */
                val writer = new FileWriter(Config.failOutFile, true)
                writer write content
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

        val newItem: Boolean = item.pubDate after lastBuildDate
        val emptyDate: Boolean = item.pubDate.equals(new Date(0))
        // connection
        val channelColl = conn(Config.db)("channel")
        val itemColl = conn(Config.db)("item")

        val needSave: Boolean =
        newItem match {
            case false =>
                emptyDate match {
                    case true =>
                        val found = 
                            if (item.link.length > 0) {
                                val cond = MongoDBObject("link"->item.link)
                                itemColl.findOne(cond, MongoDBObject("_id"->1))
                            } else if (item.title.length > 0) {
                                val cond = MongoDBObject("title"->item.title)
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


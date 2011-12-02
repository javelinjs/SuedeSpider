package com.awkin.suedespider

import java.net.URL
import java.util.Date

import scala.io.Source
import scala.xml._
import scala.actors._
import Actor._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoConnection

class Spider(val feeds: Set[String], val conn: MongoConnection, 
                val caller: Actor) extends Actor {
    def act() {
        //Thread sleep 4000
        val channelColl = conn(Config.db)("channel")
        val itemColl = conn(Config.db)("item")
        feeds.foreach { url => 
            try {
                println("Try to crawl from %s".format(url))
                val xml = XML.load(new URL(url))
                val rss = new Rss(xml)

                //channel related
                val channelObj = MongoDBObject("link"->rss.channel_link)
                val channelInsLast = channelColl.findOne(channelObj, 
                                            MongoDBObject("lastBuildDate"->1))
                val lastBuildDate = 
                    try {
                        channelInsLast.get("lastBuildDate").asInstanceOf[Date]
                    } catch {
                        case _ => new Date(0)
                    }
                // update the channel
                val channelInsNew = channelColl.findAndModify(channelObj, 
                                    MongoDBObject("_id"->1),
                                    MongoDBObject(), false,
                                    $set("title"->rss.channel_title,
                                            "desc"->rss.channel_desc,
                                            "lastBuildDate"->rss.lastBuildDate,
                                            "buildFreq"->rss.buildFreq), 
                                    true, true)

                //items related
                val items = rss.items(lastBuildDate)
                items.foreach { item => 
                    val itemObj = 
                            MongoDBObject("channel"->channelInsNew.get("_id"),
                                        "title"->item.title,
                                        "desc"->item.desc,
                                        "content"->item.content,
                                        "link"->item.link,
                                        "pubDate"->item.pubDate)
                    itemColl += itemObj
                }
            } catch {
                case _ => 
                    println("Fail to crawl from %s".format(url))
            }
            //XML save (title + ".xml", xml, "UTF-8")
        }
        caller ! (true, self)
    }
}


package com.awkin.suedespider

import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

import scala.xml._

class Rss(val rss: Elem) {
    val channelNode = rss \ "channel"

    def lastBuildDate = {
        val lastBuildDateStr =  (channelNode \ "lastBuildDate").text
        val dateStr = 
            if (lastBuildDateStr.length > 0) lastBuildDateStr
            else (channelNode \ "pubDate").text
        parseDate(dateStr)
    }
    def channel_title = (channelNode \ "title").text
    def channel_link = (channelNode \ "link").text
    def channel_desc = (channelNode \ "description").text
    def buildFreq = 0.5

    //def items(lastBuild: Date = new Date(0)) = {
    def items: List[RssItem] = {
        (List[RssItem]() /: (channelNode \ "item")) { (list, item) =>
            val pubDate = parseDate((item \ "pubDate").text)
            val title = (item \ "title").text
            val link = (item \ "link").text
            val desc = (item \ "description").text
            val content = (item \ "content").text
            list ::: List(RssItem(title, link, desc, content, pubDate))
        }
    }

    private def parseDate(dateStr: String): Option[Date] = {
        val dformat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US)
        dformat setTimeZone TimeZone.getTimeZone("+0800")
        try {
            Some(dformat.parse(dateStr))
        } catch {
            case _ => 
            try {
                val sformat = new SimpleDateFormat("yyyy HH:mm:ss z", Locale.US)
                //获取datestr中最后三个元素组成简化版的date
                val len = dateStr.length
                val array = dateStr.split(" ")
                val newstr = "%s %s %s".format(array(len-1), array(len-2), array(len-3))
                Some(sformat.parse(newstr))
            } catch {
                case _ =>
                    /* TODO: WARNING 
                    println("Fail to parse date %s for [%s], set to current time".format(
                                dateStr, channel_link)) */
                    None
            }
        }
    }
}

class RssItem(val title: String, val link: String, 
                val desc: String, val content: String,
                val pubDate: Option[Date]) {
}
object RssItem {
    def apply(title: String, link: String, 
                desc: String, content: String,
                pubDate: Option[Date]) = {
        new RssItem(title, link, desc, content, pubDate)
    }
}

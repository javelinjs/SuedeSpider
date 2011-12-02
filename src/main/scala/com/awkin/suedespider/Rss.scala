package com.awkin.suedespider

import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

import scala.xml._

class Rss(val rss: Elem) {
    val dformat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US)
    dformat setTimeZone TimeZone.getTimeZone("+0800")
    val channelNode = rss \ "channel"

    def lastBuildDate = parseDate((channelNode \ "lastBuildDate").text)
    def channel_title = (channelNode \ "title").text
    def channel_link = (channelNode \ "link").text
    def channel_desc = (channelNode \ "description").text
    def buildFreq = 0.5
    def items(lastBuild: Date = new Date(0)) = {
        (List[RssItem]() /: (channelNode \ "item")) { (list, item) =>
            val pubDate = parseDate((item \ "pubDate").text)
            if (pubDate after lastBuild) {
                val title = (item \ "title").text
                val link = (item \ "link").text
                val desc = (item \ "description").text
                val content = (item \ "content").text
                list ::: List(RssItem(title, link, desc, content, pubDate))
            } else {
                list
            }
        }
    }

    private def parseDate(dateStr: String): Date = {
        try {
            dformat parse dateStr
        } catch {
            case _ => new Date(0)
        }
    }
}

class RssItem(val title: String, val link: String, 
                val desc: String, val content: String,
                val pubDate: Date) {
}
object RssItem {
    def apply(title: String, link: String, 
                desc: String, content: String,
                pubDate: Date) = {
        new RssItem(title, link, desc, content, pubDate)
    }
}

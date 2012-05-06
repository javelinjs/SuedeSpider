package com.awkin.suedespider

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoDB

object FeatureExtractor {
    def apply(itemId: Option[ObjectId], rssItem: RssItem) = {
        MongoDBObject(
            "item"->itemId,
            "length_title"->titleLength(rssItem),
            "length_desc"->descLength(rssItem),
            "length_content"->contentLength(rssItem),
            "source"->source(rssItem)
        )
    }
    def titleLength(item: RssItem): Int = item.title.length
    def descLength(item: RssItem): Int = {
        if (item.content.length == 0) Config.descMaxLength
        else item.desc.length
    }
    def contentLength(item: RssItem): Int = {
        if (item.content.length == 0) item.desc.length
        else item.content.length
    }
    def source(item: RssItem): String = item.link
}

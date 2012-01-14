package com.awkin.suedespider

import org.json._
import java.io._

import scala.io.Source

class Config private {
    var pair: JSONObject = new JSONObject()

    /* Default settings */
    //val numOfJobPerThreadDef: Int = 2
    val jobThreadNumDef: Int = 2
    val maxSpiderWaitTimeDef: Int = 40
    val feedFileDef: String = "config/data/feeds.txt"
    val failOutFileDef: String = "failToCrawl.txt"
    val dbDef: String = "awkin"
    /* how many seconds the Alarm check for new channel to crawl */
    val alarmCheckIntervalDef: Int = 60
    val alarmCheckIntervalMaxDef: Int = 86400 //One day
    val alarmCheckFreqInitDef = 1 //used in Alarm:feedListInit 
}

object Config {
    private val conf = new Config()
    private val confFile = "config/suede.conf"

    def jobThreadNum = 
        conf.pair.optInt("job_thread_num", conf.jobThreadNumDef)
    def maxSpiderWaitTime = 
        conf.pair.optInt("max_spider_wait_time", conf.maxSpiderWaitTimeDef)
    def db = 
        conf.pair.optString("db", conf.dbDef)
    def feedFile = 
        conf.pair.optString("feed_file", conf.feedFileDef)
    def failOutFile = 
        conf.pair.optString("fail_out_file", conf.failOutFileDef)
    def alarmCheckInterval = 
        conf.pair.optInt("alarm_check_interval", conf.alarmCheckIntervalDef)
    def alarmCheckIntervalMax = 
        conf.pair.optInt("alarm_check_interval_max", conf.alarmCheckIntervalMaxDef)
    def alarmCheckFreqInit = 
        conf.pair.optInt("alarm_check_freq_init", conf.alarmCheckFreqInitDef)

    def readConf() {
        try {
            Source.fromFile(confFile).getLines.foreach { line =>
                line match {
                case notComment() =>
                    try {
                        val setting = new JSONObject("{" + line + "}")
                        val key = setting.keys.next.toString()
                        conf.pair.put(key, setting.get(key))
                    } catch {
                        case _ =>
                            val ex = new InvalidConfSetting(confFile, line)
                            println(ex.getMessage())
                    }
                case isComment() => 
                }
            }
        } catch {
            case exFile: FileNotFoundException =>
                val ex = new InvalidConfFile(confFile)
                println(ex.getMessage)
                println("[Config] Use default config")
            case exUnknown =>
                throw exUnknown
        }
    }
}

object isComment {
    def apply(symbol: String) : Boolean = symbol(0) == '#'
    def unapply(symbol: String) : Boolean = symbol(0) == '#'
}
object notComment {
    def apply(symbol: String) : Boolean = !isComment.apply(symbol)
    def unapply(symbol: String) : Boolean = !isComment.unapply(symbol)
}

/* Exceptions */
class InvalidConfFile(val confFile: String) extends Exception {
    override def getMessage() = {
        "No such conf file: %s".format(confFile)
    }
}
class InvalidConfSetting(val confFile: String, val setting: String) extends Exception {
    override def getMessage() = {
        "Invalid setting of %s in file %s".format(setting, confFile)
    }
}

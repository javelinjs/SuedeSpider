package com.awkin.suedespider

import org.json._
import java.io._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter

import scala.io.Source

class Config private {
    val logger = LoggerFactory.getLogger(classOf[Config])

    var pair: JSONObject = new JSONObject()

    /* Default settings */
    //val numOfJobPerThreadDef: Int = 2
    val jobThreadNumDef: Int = 2
    val maxSpiderWaitTimeDef: Int = 40
    val feedFileDef: String = "config/data/feeds.txt"
    val failOutFileDef: String = "failToCrawl.txt"
    /* db config */
    val dbDef: String = "awkin"
    val dbHostDef: String = "localhost"
    val dbPortDef: Int = 27017
    val dbUserDef: String = ""
    val dbPwdDef: String = ""
    /* how many seconds the Alarm check for new channel to crawl */
    val alarmCheckIntervalDef: Int = 60
    val alarmCheckIntervalMaxDef: Int = 86400 //One day
    val alarmCheckFreqInitDef = 1 //used in Alarm:feedListInit 
    /* for how many days should test the duplicate items */
    val dupItemCheckDayDef = 1
    /* whether it is run as a daemon */
    val runAsDaemonDef = true
}

object Config {
    private val conf = new Config()
    private val confFileDef = "config/suede.conf"

    def jobThreadNum = 
        conf.pair.optInt("job_thread_num", conf.jobThreadNumDef)
    def maxSpiderWaitTime = 
        conf.pair.optInt("max_spider_wait_time", conf.maxSpiderWaitTimeDef)
    def db = 
        conf.pair.optString("db", conf.dbDef)
    def dbHost = 
        conf.pair.optString("db_host", conf.dbHostDef)
    def dbPort = 
        conf.pair.optInt("db_port", conf.dbPortDef)
    def dbUser = 
        conf.pair.optString("db_user", conf.dbUserDef)
    def dbPwd = 
        conf.pair.optString("db_pwd", conf.dbPwdDef)
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
    def dupItemCheckDay = 
        conf.pair.optInt("dup_item_check_day", conf.dupItemCheckDayDef)
    def runAsDaemon = 
        conf.pair.optBoolean("run_as_daemon", conf.runAsDaemonDef)

    def readConf(filename: Option[String] = None) {
        val confFile = filename.getOrElse(confFileDef)
        conf.logger.info("read config from {}", confFile)
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
                            conf.logger.warn(ex.getMessage())
                    }
                case isComment() => 
                }
            }
        } catch {
            case exFile: FileNotFoundException =>
                val ex = new InvalidConfFile(confFile)
                conf.logger.warn(ex.getMessage)
                conf.logger.info("use default config")
            case exUnknown =>
                conf.logger.error("unable to read config from {}", confFile)
                throw exUnknown
        }
    }
}

object isComment {
    def apply(symbol: String) : Boolean = (symbol == "" || symbol(0) == '#')
    def unapply(symbol: String) : Boolean = (symbol == "" || symbol(0) == '#')
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

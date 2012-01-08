package com.awkin.suedespider

class Config private {
    private var numOfJobPerThread: Int = _
    private var maxSpiderWaitTime: Int = _
    private var feedFile: String = _
    private var failOutFile: String = _
    private var db: String = _
}

object Config {
    private val conf = new Config()
    def readConf(): Boolean = {
        conf numOfJobPerThread_= 2 
        conf maxSpiderWaitTime_= 40000
        conf db_= "awkin"
        conf feedFile_= "config/data/feeds.txt"
        conf failOutFile = "fail_to_crawl.txt"
        true
    }

    def numOfJobPerThread = conf.numOfJobPerThread
    def maxSpiderWaitTime = conf.maxSpiderWaitTime
    def db = conf.db
    def feedFile = conf.feedFile
    def failOutFile = conf.failOutFile
}

object isComment {
    def apply(symbol: String) : Boolean = symbol(0) == '#'
    def unapply(symbol: String) : Boolean = symbol(0) == '#'
}

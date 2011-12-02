package com.awkin.suedespider

class Config private {
    private var numOfJobPerThread: Int = _
    private var maxSpiderWaitTime: Int = _
    private var db: String = _
}

object Config {
    private val conf = new Config()
    def readConf(): Boolean = {
        conf numOfJobPerThread_= 2 
        conf maxSpiderWaitTime_= 40000
        conf db_= "awkin"
        true
    }

    def numOfJobPerThread = conf.numOfJobPerThread
    def maxSpiderWaitTime = conf.maxSpiderWaitTime
    def db = conf.db
}

object isComment {
    def apply(symbol: String) : Boolean = symbol(0) == '#'
    def unapply(symbol: String) : Boolean = symbol(0) == '#'
}

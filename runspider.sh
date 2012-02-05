#!/bin/bash
#cd /path_to_self
PROGNAME="shotwell"

pid=""
found="false"
if [ -f ${PROGNAME}.pid ]; then
    pid=`cat ${PROGNAME}.pid | awk '{print $1}'`
    pidrun_list=`ps ax | grep ${PROGNAME} | awk '{print $1}'`

    for pidrun in ${pidrun_list}
    do
        if [ ${pidrun} -eq ${pid} ]; then
            found="true"
        fi
    done
fi

if [ ${found} = "false" ]; then
    echo "`date '+%Y-%m-%d %H:%M:%S'` Run-spider" >> ./run.log
    #/usr/java/jdk1.7.0_02/bin/java -Dlogback.configurationFile=../logback.xml -jar suedespider-0.1.jar ./config/suede.conf
    shotwell &
    echo $! > ${PROGNAME}.pid
else
    echo "`date '+%Y-%m-%d %H:%M:%S'` Spider-not-end" >> ./run.log
    sleep 600 #10 mins
    kill -15 $pid
fi


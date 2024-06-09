#!/bin/sh

adb shell 'pm path radio.ab3j.nfc | sed "s/^package:/export CLASSPATH=/" > /data/local/tmp/nfcterm.env'

adb shell 'echo "source /data/local/tmp/nfcterm.env" >> /data/local/tmp/nfcterm.sh'
adb shell 'echo "app_process / radio.ab3j.nfc.ShellMain $@" >> /data/local/tmp/nfcterm.sh'

adb shell 'chmod +x /data/local/tmp/nfcterm.sh'

adb shell '/bin/sh /data/local/tmp/nfcterm.sh'


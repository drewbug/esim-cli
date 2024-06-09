#!/bin/sh
adb shell "CLASSPATH=\$(pm path radio.ab3j.esim) app_process / radio.ab3j.esim.ShellMain $@"

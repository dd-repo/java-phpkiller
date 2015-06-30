#!/bin/sh
cd $(dirname $0)
[ -d ./bin ] || mkdir -p ./bin && rm -rf ./bin/*
cd ./src && javac -d ../bin -cp "../bin/" com/olympe/*.java || exit 1
cd ../bin && echo 'Main-Class: com.olympe.phpkiller' > manifest.txt && jar cfm ../phpkiller.jar manifest.txt com/olympe/*.class || exit 2
cd .. && rm -rf bin

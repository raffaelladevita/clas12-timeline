#!/bin/bash

if [ $1 = "setup" ]; then
	echo "downloading detector timeline"
	
	mkdir ../detectors
	cd ../detectors
	wget https://github.com/Sangbaek/run_based_monitoring/releases/download/v1.0/run.sh
	wget https://github.com/Sangbaek/run_based_monitoring/releases/download/v1.0/timelineMon-1.0-SNAPSHOT.jar
	chmod +x run.sh
	mkdir bin
	mkdir target
	mv run.sh bin/
	mv timelineMon-1.0-SNAPSHOT.jar target/
else
	echo "running detector timeline"
	cd ../detectors
	./bin/run.sh $1
fi
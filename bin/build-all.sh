#!/usr/bin/bash

cd monitoring
mvn package
cd -
cd detectors
mvn package
cd -

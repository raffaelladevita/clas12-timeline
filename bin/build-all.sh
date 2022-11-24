#!/usr/bin/bash

SCRIPTPATH=`realpath $0`
BINPATH=`dirname $SCRIPTPATH`
DIRPATH=`dirname $BINPATH`

cd $DIRPATH/monitoring
mvn package
cd -
cd $DIRPATH/detectors
mvn package
cd -

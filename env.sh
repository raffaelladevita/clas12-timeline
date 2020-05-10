#!/bin/bash
CLASSPATH="${CLASSPATH}:${COATJAVA}/lib/clas/*"
CLASSPATH="${CLASSPATH}:${PWD}"
export CLASSPATH=$(echo $CLASSPATH | sed 's/^://')

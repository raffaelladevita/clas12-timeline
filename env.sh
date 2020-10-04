#!/bin/bash

export CLASQA=$(dirname $(realpath $0))
JYPATH="${JYPATH}:${CLASQA}"
export JYPATH=$(echo $JYPATH | sed 's/^://')

echo "Environment:"
env | grep -w COATJAVA
env | grep -w CLASQA
env | grep -w JYPATH

# proposed workaround to include extra options
# (run-groovy overwrites JAVA_OPTS, but accepts additional arguments)
#alias run-groovy='run-groovy -Djava.awt.headless=true'
#echo "NOTE: run-groovy has been aliased to:"
#echo `alias run-groovy`

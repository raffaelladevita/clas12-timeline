#!/bin/bash

export CLASQA=$(dirname $(realpath $0))
JYPATH="${JYPATH}:${CLASQA}"
export JYPATH=$(echo $JYPATH | sed 's/^://')

echo "Environment:"
env | grep -w COATJAVA
env | grep -w CLASQA
env | grep -w JYPATH

#!/bin/bash

export CLASQA=$(dirname $(realpath $0))
JYPATH="${JYPATH}:${CLASQA}"
export JYPATH=$(echo $JYPATH | sed 's/^://')

echo "Environment:"
p() { env|grep --color -w $1; }
p COATJAVA
p CLASQA
p JYPATH

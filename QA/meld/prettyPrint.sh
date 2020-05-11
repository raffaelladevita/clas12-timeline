#!/bin/bash
# (called by meld.sh)
groovy ../../jprint.groovy qaTree.json.$1 > qaTree.json.${1}.pprint
groovy ../parseQaTree.groovy qaTree.json.$1

#!/bin/bash
# pretty print a json file (called by meld.sh)
groovy ../../jprint.groovy qaTree.json.$1 > qaTree.json.${1}.pprint

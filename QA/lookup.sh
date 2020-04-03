#!/bin/bash
# pretty print a part of qaTree.json (specify tree path as arguments)
groovy ../jprint.groovy qa/qaTree.json $*

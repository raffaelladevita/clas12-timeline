#!/bin/bash
# copy a locally deployed timeline to the release directory

if [ $# -ne 1 ]; then 
  echo "USAGE: $0 [dataset]"
  exit
fi
if [ -z "$CLASQA" ]; then
  echo: "ERROR: please source env.sh first"
  exit
fi

dataset=$1

wwwReleaseDir="${CLASQAWWW}/rga/pass1/qa"
wwwLocalDir="${CLASQAWWW}/$(whoami)"

rm -r ${wwwReleaseDir}/${dataset}*
cp -rv ${wwwLocalDir}/${dataset}* ${wwwReleaseDir}/
cp outdat.${dataset}/qaTree.json ${wwwReleaseDir}/${dataset}_QA/

run-groovy $CLASQA_JAVA_OPTS indexPage.groovy $wwwReleaseDir

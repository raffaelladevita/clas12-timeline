#!/bin/bash
# copy a locally deployed timeline to the release directory

if [ $# -ne 1 ]; then 
  echo "USAGE: $0 [dataset]"
  exit
fi

dataset=$1

wwwReleaseDir="../wwwRelease"
wwwLocalDir="../wwwDev"

rm -r ${wwwReleaseDir}/${dataset}*
cp -rv ${wwwLocalDir}/${dataset}* ${wwwReleaseDir}/
cp outdat.${dataset}/qaTree.json ${wwwReleaseDir}/${dataset}_QA/

groovy indexPage.groovy $wwwReleaseDir

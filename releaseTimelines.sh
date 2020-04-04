#!/bin/bash
# copy a locally deployed timeline to the release directory

if [ $# -ne 1 ]; then 
  echo "USAGE: $0 [dataset]"
  exit
fi

dataset=$1

wwwReleaseDir="../www/rga/pass1/qa"
wwwLocalDir="../www/${USER}"

rm -r ${wwwReleaseDir}/${dataset}*
cp -rv ${wwwLocalDir}/${dataset}* ${wwwReleaseDir}/

groovy indexPage.groovy $wwwReleaseDir

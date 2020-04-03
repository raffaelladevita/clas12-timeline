#!/bin/bash
# copy a locally deployed timeline to the release directory

if [ $# -ne 1 ]; then 
  echo "USAGE: $0 [dataset]"
  exit
fi

dataset=$1

wwwReleaseDir="../www/rga/pass1/qa"
wwwLocalDir="../www/${USER}"

mkdir -p ${wwwReleaseDir}/${dataset}
mkdir -p ${wwwReleaseDir}/${dataset}_extra
rm -r ${wwwReleaseDir}/${dataset}
rm -r ${wwwReleaseDir}/${dataset}_extra
mkdir -p ${wwwReleaseDir}/${dataset}
mkdir -p ${wwwReleaseDir}/${dataset}_extra

cp -v ${wwwLocalDir}/${dataset}/* ${wwwReleaseDir}/${dataset}/
cp -v ${wwwLocalDir}/${dataset}_extra/* ${wwwReleaseDir}/${dataset}_extra/

groovy indexPage.groovy $wwwReleaseDir

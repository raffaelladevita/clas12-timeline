#!/bin/bash
# copy timeline hipo files to webserver area 
# - must be run on ifarm

if [ $# -ne 1 ]; then
  echo "usage: $0 [subdirectory]"
  exit
fi

subdir=$1

wwwdir="/group/clas/www/clas12mon/html/hipo/${USER}"
mondir="outmon"
pushd $mondir
for file in `ls *.hipo | grep -v "monitor_"`; do
  cp -v $file ${wwwdir}/${subdir}/${file}
done
popd

#!/bin/bash
# copy timeline hipo files to webserver area 
# - must be run on ifarm
# - optional argument will prepend a prefix to the timeline file name

if [ $# -eq 1 ]; then prefix="${1}_"
else prefix=""; fi

wwwdir="/group/clas/www/clas12mon/html/hipo/${USER}"
mondir="outmon"
pushd $mondir
for file in `ls *.hipo | grep -v "monitor_"`; do
  cp -v $file ${wwwdir}/${prefix}${file}
done
popd

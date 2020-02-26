#!/bin/bash
# copy timeline hipo files to webserver area 
# - must be run on ifarm
# - prefix argument will prepend a prefix to the timeline file name

if [ $# -ne 2 ]; then
  echo "usage: $0 [dataset] [timelinePrefix]"
  exit
fi

dataset=$1
prefix=$2

wwwdir="/group/clas/www/clas12mon/html/hipo/${USER}"
pushd outhipo.${dataset}
for file in electron*.hipo; do
  cp -v $file ${wwwdir}/${prefix}_${file}
done
popd

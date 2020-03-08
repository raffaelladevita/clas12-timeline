#!/bin/bash
# copy timeline hipo files to webserver area 
# - must be run on ifarm

if [ $# -ne 2 ]; then
  echo "usage: $0 [dataset] [subdirectory]"
  exit
fi

dataset=$1
subdir=$2

wwwdir="/group/clas/www/clas12mon/html/hipo/${USER}"
pushd outhipo.${dataset}
for file in `ls *.hipo | grep -v "plots.hipo"`; do
  cp -v $file ${wwwdir}/${subdir}/${file}
done
popd

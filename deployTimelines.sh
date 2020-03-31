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
for file in `ls *.hipo | grep -v "monitor"`; do
  mkdir -p ${wwwdir}/${subdir}
  cp -v $file ${wwwdir}/${subdir}/${file}
done
popd

pushd $wwwdir
extradir=${subdir}_extra
mkdir -p $extradir
function mvextra { mv -v ${subdir}/$1 ${extradir}/; }
mvextra electron_FT_yield_QA_epoch_view.hipo
mvextra electron_FT_yield_stddev.hipo
mvextra electron_FT_yield_values.hipo
mvextra electron_trigger_yield_QA_epoch_view.hipo
mvextra electron_trigger_yield_stddev.hipo
mvextra electron_trigger_yield_values.hipo
mvextra faraday_cup_stddev.hipo
mvextra helicity_sinPhi.hipo
popd

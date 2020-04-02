#!/bin/bash
# copy timeline hipo files to webserver area 
# - must be run on ifarm

if [ $# -ne 2 ]; then
  echo "usage: $0 [dataset] [destinationName]"
  exit
fi

dataset=$1
destdir=$2

wwwdir="../www/${USER}" # symlink www

mkdir -p ${wwwdir}/${destdir}
rm -r ${wwwdir}/${destdir}
mkdir -p ${wwwdir}/${destdir}
for file in `ls outmon.${dataset}/*.hipo | grep -v "monitor"`; do
  cp -v $file ${wwwdir}/${destdir}/
done

pushd $wwwdir
extradir=${destdir}_extra
mkdir -p $extradir
rm -r $extradir
mkdir -p $extradir
function mvextra { mv -v ${destdir}/$1 ${extradir}/; }
mvextra electron_FT_yield_QA_epoch_view.hipo
mvextra electron_FT_yield_stddev.hipo
mvextra electron_FT_yield_values.hipo
mvextra electron_trigger_yield_QA_epoch_view.hipo
mvextra electron_trigger_yield_stddev.hipo
mvextra electron_trigger_yield_values.hipo
mvextra faraday_cup_stddev.hipo
mvextra helicity_sinPhi.hipo
popd

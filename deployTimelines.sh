#!/bin/bash
# copy timeline hipo files to webserver area 
# - must be run on ifarm

if [ $# -ne 1 ]; then
  echo "usage: $0 [subdirectory]"
  exit
fi

subdir=$1

wwwdir="../www/${USER}" # symlink www

mkdir -p ${wwwdir}/${subdir}
rm -r ${wwwdir}/${subdir}
mkdir -p ${wwwdir}/${subdir}
for file in `ls outmon/*.hipo | grep -v "monitor"`; do
  cp -v $file ${wwwdir}/${subdir}/
done

pushd $wwwdir
extradir=${subdir}_extra
mkdir -p $extradir
rm -r $extradir
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

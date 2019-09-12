#!/bin/bash

mkdir -p datfiles
mkdir -p logfiles

ls ../monsub/monplots_*.hipo > filelist
cat filelist | sed -r 's/([^_]*)$//g' | sort | uniq > tempo

let cnt=1
let njobs=16

while read fname; do
  if [ $cnt -le $njobs ]; then
    logname="logfiles/$(echo $fname | sed 's/^.*\///g' | sed 's/_$/\.log/')"
    echo $logname
    #groovy qaElec.groovy ${fname}* 2>&1 > logfiles/${logname} &
    let cnt++
  else
    wait
    let cnt=1
  fi
done < tempo

nfiles=$(cat filelist | wc -l)
noutput=$(cat datfiles/*.dat | awk '{print $1" "$2}' | sort | uniq | wc -l)
echo "$nfiles files found"
echo "$noutput files analyzed"

rm tempo filelist

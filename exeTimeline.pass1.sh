#!/bin/bash

if [ -z "$CLASQA" ]; then source env.sh; fi

if [ $# -ne 1 ];then echo "USAGE: $0 [dataset]"; exit; fi
dataset=$1

# setup error filtered execution function
errlog="errors.log"
> $errlog
function sep { printf '%70s\n' | tr ' ' -; }
function exe { 
  sep
  echo "EXECUTE: $*"
  sep
  sep >> $errlog
  echo "$* errors:" >> $errlog
  $* 2>>$errlog
}

# organize the data into datasets
exe ./datasetOrganize.sh

# loop over datasets
# trigger electrons monitor
exe run-groovy qaPlot.groovy $dataset
exe run-groovy qaCut.groovy $dataset
# FT electrons
exe run-groovy qaPlot.groovy $dataset FT
exe run-groovy qaCut.groovy $dataset FT
# general monitor
exe run-groovy monitorPlot.groovy $dataset
# deploy timelines to dev www
exe ./deployTimelines.sh $dataset $dataset


# print errors (filtering out hipo logo contamination)
sep
echo "TIMELINE GENERATION COMPLETE"
grep -vE '█|═|Physics Division|^     $' $errlog
rm $errlog

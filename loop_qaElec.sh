#!/bin/bash

# set monsub directory, which contains monplots_${RUN}_${FILE}.hipo files
# default is ../monsub
monsubdir="../monsub";
outputpng=0
if [ $# -ge 1 ]; then monsubdir=$1; fi
if [ $# -ge 2 ]; then outputpng=$2; fi


# maximum number of jobs to run in parallel
let njobs=4


# if njobs > number of available threads, set njobs to that number
nthreads=$(grep -c processor /proc/cpuinfo)
if [ $njobs -gt $nthreads ]; then njobs=$nthreads; fi
echo "will run a maximum of $njobs jobs in parallel"


# clean output and log dirs
rm -rf outdat/*.dat
rm -rf outbad/*.dat
rm -rf outhipo/*.hipo
rm -rf logfiles/*.{err,out}


# build list of monsub files, stripping off file numbers
ls ../monsub/monplots_*.hipo | sed 's/^.*\///' | cut -d'_' -f2 | uniq > runlist.tmp


# loop over runs
let cnt=1
while read run; do
  log="logfiles/job.$run"
  echo "analyzing run $run"
  groovy qaElec.groovy $run $monsubdir $outputpng 1 > ${log}.out 2> ${log}.err &
  if [ $cnt -lt $njobs ]; then
    let cnt++
  else
    wait
    exit # prematurely
    let cnt=1
  fi
done < runlist.tmp


# concatenate
wait
cat outdat/mondata*.dat > outdat/all.dat
cat outbad/outliers*.dat > outbad/bad.dat

# cleanup
rm runlist.tmp

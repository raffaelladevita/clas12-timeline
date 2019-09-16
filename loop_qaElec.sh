#!/bin/bash

# set monsub directory, which contains monplots_${RUN}_${FILE}.hipo files
# default is ../monsub
if [ $# -eq 1 ]; then monsubdir=$1;
else monsubdir="../monsub"; fi


# maximum number of jobs to run in parallel
let njobs=16


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
  if [ $cnt -le $njobs ]; then
    log="logfiles/job.$run"
    echo "analyzing run $run"
    groovy qaElec.groovy $run $monsubdir 1 > ${log}.out 2> ${log}.err &
    let cnt++
  else
    wait
    let cnt=1
  fi
done < runlist.tmp


# concatenate
wait
cat outdat/mondata*.dat > outdat/all.dat
cat outbad/outliers*.dat > outbad/all.dat

# cleanup
rm runlist.tmp

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
rm -r outdat/*.dat
rm -r logfiles/*.{err,out}


# build list of monsub files, stripping off file numbers
ls ../monsub/monplots_*.hipo > filelist
cat filelist | sed -r 's/([^_]*)$//g' | sort | uniq > tempo


# loop over monsub files, executing one job per run
let cnt=1
while read fname; do
  if [ $cnt -le $njobs ]; then
    logname="logfiles/$(echo $fname | sed 's/^.*\///g' | sed 's/_$//')"
    echo "reading ${fname}*"
    groovy qaElec.groovy ${fname}* > ${logname}.out 2> ${logname}.err &
    let cnt++
  else
    wait
    let cnt=1
  fi
done < tempo

# compare number of input monsub files to number counted in outdat datfiles
wait
nfiles=$(cat filelist | wc -l)
noutput=$(cat outdat/*.dat | awk '{print $1" "$2}' | sort | uniq | wc -l)
echo "$nfiles files found"
echo "$noutput files analyzed"

# cleanup
rm tempo filelist

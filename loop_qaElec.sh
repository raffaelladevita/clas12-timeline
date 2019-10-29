#!/bin/bash

# set monsub directory, which contains monplots_${RUN}_${FILE}.hipo files
# default is ../monsub
monsubdir="../monsub";
outputpng=0
if [ $# -ge 1 ]; then monsubdir=$1; fi
if [ $# -ge 2 ]; then outputpng=$2; fi


# maximum number of jobs to run in parallel
let njobs=8


# if njobs > number of available threads, set njobs to that number
nthreads=$(grep -c processor /proc/cpuinfo)
if [ $njobs -gt $nthreads ]; then njobs=$nthreads; fi
echo "will run a maximum of $njobs jobs in parallel"


# cleanup
./cleanup.sh
rm -rf logfiles/*.{err,out}


# build list of runnums to call qaElec.groovy with runMany==true for each epoch
> epochlist.tmp
for x in $(seq 1 $(cat epochs.txt|wc -l)); do 
  echo $(echo 10000+$x|bc) >> epochlist.tmp 
done

# build list of runs
ls ${monsubdir}/monplots_*.hipo | sed 's/^.*\///' | cut -d'_' -f2 | uniq > runlist.tmp


# loop over epochlist.tmp, which will determine the QA cuts for each epoch,
# then loop over runlist.tmp to determine outliers in each run
let cnt=1
for runlist in {epoch,run}list.tmp; do
  while read run; do
    log="logfiles/job.$run"
    echo "analyzing run $run"
    groovy qaElec.groovy $run $monsubdir $outputpng 1 > ${log}.out 2> ${log}.err &
    if [ $cnt -lt $njobs ]; then
      let cnt++
    else
      wait
      let cnt=1
    fi
  done < $runlist
  echo "waiting here..."
  wait
done


# concatenate
wait
cat outbad/outliers*.dat > outbad/bad.txt

# cleanup
rm {epoch,run}list.tmp

#!/bin/bash
# builds timeline hipo files and stores them in outmon; also deploys them
# to the webserver; this script is to be run after all slurm jobs launched
# by exeMonitor.pass1.sh have finished

# clean outmon directory
for file in `ls outmon/*.hipo | grep -v "monitor"`; do
  rm -v $file
done

# setup error filter
errlog="errors.log"
> $errlog
function errdiv { 
  printf '%70s\n' | tr ' ' - >> $errlog
  echo "$* errors:" >> $errlog
}

# concatenate data_table
mkdir -p outdat.pass1
> outdat.pass1/data_table.dat
for file in outdat/data_table*.dat; do
  cat $file >> outdat.pass1/data_table.dat
done

# trigger electrons
errdiv qaPlot.groovy
groovy qaPlot.groovy pass1 2>>$errlog
errdiv qaCut.groovy
groovy qaCut.groovy pass1 2>>$errlog

# FT electrons
errdiv qaPlot.groovy FT
groovy qaPlot.groovy pass1 FT 2>>$errlog
errdiv qaCut.groovy FT
groovy qaCut.groovy pass1 FT 2>>$errlog

# generalized monitor
errdiv monitorPlot.groovy
groovy monitorPlot.groovy 2>>$errlog

# print errors (filtering out hipo logo contamination)
printf '%70s\n' | tr ' ' -
echo "TIMELINE GENERATION COMPLETE"
grep -vE '█|═|Physics Division|^     $' errors.log
rm $errlog

printf '%70s\n' | tr ' ' -
echo "execute 'deployTimelines.sh pass1' to upload to webserver"


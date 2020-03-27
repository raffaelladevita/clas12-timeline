#!/bin/bash
# builds timeline hipo files and stores them in outmon; also deploys them
# to the webserver; this script is to be run after all slurm jobs launched
# by exeMonitor.pass1.sh have finished

# clean outmon directory
for file in `ls outmon/*.hipo | grep -v "monitor"`; do
  rm -v $file
done

# electron QA monitor
mkdir -p outdat.pass1
> outdat.pass1/data_table.dat
for file in outdat/data_table*.dat; do
  cat $file >> outdat.pass1/data_table.dat
done
groovy qaPlot.groovy pass1
groovy qaCut.groovy pass1

# generalized monitor
groovy monitorPlot.groovy

# copy to webserver
deployTimelines.sh pass1

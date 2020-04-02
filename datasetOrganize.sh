#!/bin/bash

# list of datasets; edit if statements below to specify run ranges
> datasetList.dat
echo inbending1 >> datasetList.dat
echo inbending2 >> datasetList.dat

# cleanup / generate new dataset subdirs
while read dataset; do
  for outdir in outmon outdat; do
    dir=${outdir}.${dataset}
    echo "clean $dir"
    mkdir -p $dir
    rm -r $dir
    mkdir -p $dir
  done
done < datasetList.dat

# loop over runs, copying and linking to dataset subdirs
for file in outmon/monitor_*.hipo; do
  run=$(echo $file | sed 's/^.*monitor_//'|sed 's/\.hipo$//')

  # determine which dataset this run belongs to
  if [ $run -ge 5032 -a $run -le 5262 ]; then
    dataset="inbending1"
  elif [ $run -ge 5263 -a $run -le 5419 ]; then 
    dataset="inbending2"
  else 
    >&2 echo "ERROR: run $run has unknown dataset"
    continue
  fi

  cat outdat/data_table_${run}.dat >> outdat.${dataset}/data_table.dat
  ln -sv `pwd`/outmon/monitor_${run}.hipo ./outmon.${dataset}/monitor_${run}.hipo
done

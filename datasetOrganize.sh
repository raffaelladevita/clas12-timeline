#!/bin/bash

# cleanup / generate new dataset subdirs
while read line; do
  dataset=$(echo $line|awk '{print $1}')
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
  dataset=""
  while read line; do
    runL=$(echo $line|awk '{print $2}')
    runH=$(echo $line|awk '{print $3}')
    if [ $run -ge $runL -a $run -le $runH ]; then
      dataset=$(echo $line|awk '{print $1}')
    fi
  done < datasetList.dat
  
  # if the dataset is associated to a run, import its data
  if [ -n "$dataset" ]; then
    echo "file run $run to dataset $dataset"
    cat outdat/data_table_${run}.dat >> outdat.${dataset}/data_table.dat
    ln -sv `pwd`/outmon/monitor_${run}.hipo ./outmon.${dataset}/monitor_${run}.hipo
  else 
    >&2 echo "ERROR: run $run has unknown dataset"
    continue
  fi

done

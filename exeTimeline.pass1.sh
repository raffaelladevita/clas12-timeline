#!/bin/bash

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

# organize the data into dataset
exe ./datasetOrganize.sh

# loop over datasets
while read line; do
  dataset=$(echo $line|awk '{print $1}')
  # trigger electrons monitor
  exe groovy qaPlot.groovy $dataset
  exe groovy qaCut.groovy $dataset
  # FT electrons
  exe groovy qaPlot.groovy $dataset FT
  exe groovy qaCut.groovy $dataset FT
  # general monitor
  exe groovy monitorPlot.groovy $dataset
  # deploy timelines to dev www
  exe ./deployTimelines.sh $dataset $dataset
done < datasetList.dat


# print errors (filtering out hipo logo contamination)
sep
echo "TIMELINE GENERATION COMPLETE"
grep -vE '█|═|Physics Division|^     $' $errlog
rm $errlog

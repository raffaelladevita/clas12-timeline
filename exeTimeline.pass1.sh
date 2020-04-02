#!/bin/bash

# setup error filtered execution function
errlog="errors.log"
> $errlog
function exe { 
  printf '%70s\n' | tr ' ' - >> $errlog
  echo "$* errors:" >> $errlog
  $* 2>>$errlog
}

# organize the data into dataset
exe ./datasetOrganize.sh

# loop over datasets
while read dataset; do
  # trigger electrons monitor
  exe groovy qaPlot.groovy $dataset
  exe groovy qaCut.groovy $dataset
  # FT electrons
  exe groovy qaPlot.groovy $dataset FT
  exe groovy qaCut.groovy $dataset FT
  # general monitor
  exe groovy monitorPlot.groovy $datset
done < datasetList.dat


# print errors (filtering out hipo logo contamination)
printf '%70s\n' | tr ' ' -
echo "TIMELINE GENERATION COMPLETE"
grep -vE '█|═|Physics Division|^     $' $errlog
rm $errlog

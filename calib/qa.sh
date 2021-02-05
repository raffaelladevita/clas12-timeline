#!/bin/bash
# runs calibration QA on specified timeline directory
# - output directory path will be the same as timeline directory path, with "_qa"
#   appended to the directory name
# - dataset name is determined automatically and added to `datasetList.txt`

# arguments
if [ -z "$CLASQA" ]; then
  echo "ERROR: please source ../env.sh first"
  exit
fi
if [ $# -lt 1 ]; then
  echo "USAGE: $0 [path to timelines directory]"
  echo " - path must be relative to CLASQAWWW = $CLASQAWWW"
  echo " - or use 'util/runQA.sh [dataset]' for testing"
  exit
fi

# define dataset, and input/output timelines directories
indir=$(echo $1 | sed 's/\/$//g')
outdir=${indir}_qa
dataset=$(echo $indir | sed 's/\//_/g')
echo "$dataset $indir $outdir" >> datasetList.txt

# run the qa
${CLASQA}/calib/util/runQA.sh $dataset

#!/bin/bash

# arguments
if [ -z "$CLASQA" ]; then
  echo "ERROR: please source ../env.sh first"
  exit
fi
if [ $# -lt 1 ]; then
  echo "USAGE: $0 [dataset]"
  echo " - datasets are defined in datasetList.txt:"
  cat datasetList.txt
  exit
fi
dataset=$1


# get timeline directory names
indir=$(grep $dataset datasetList.txt | awk '{print $2}')
outdir=$(grep $dataset datasetList.txt | awk '{print $3}')
if [ -z "$indir" ]; then
  echo "ERROR: dataset not foundin datasetList.txt"
  exit
fi
indir="${CLASQAWWW}/${indir}"
outdir="${CLASQAWWW}/${outdir}"


# prepare output directory
mkdir -p $outdir; rm -r $outdir; mkdir -p $outdir
cp -rv ${indir}/* ${outdir}/


# apply QA bounds
run-groovy util/applyBounds.groovy $dataset


# cleanup output directory
echo ""
echo "CLEANUP $outdir"
for f in `find $outdir -name "*.hipo" -print | grep '_QA.hipo'`; do
  g=`echo $f | sed 's/_QA\.hipo/.hipo/g'`
  rm -v $g
  echo "...since we have $f"
done


# print URL
${CLASQA}/calib/util/url.sh $dataset


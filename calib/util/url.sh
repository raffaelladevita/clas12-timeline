#!/bin/bash
# print URL of online timeline

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

#print
echo ""
echo "source timelines from:"
echo "https://clas12mon.jlab.org/${indir}/tlsummary/"
echo ""
echo "QA timelines produced at:"
echo "https://clas12mon.jlab.org/${outdir}/tlsummary/"
echo ""

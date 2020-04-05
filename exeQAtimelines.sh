#!/bin/bash
# after finishing analysis in the `QA` subdirectory, this script will call
# qaCut.groovy on the results

if [ $# -ne 1 ]; then
  echo "USAGE: $0 [dataset]"
  exit
fi
dataset=$1

qaDir=outmon.${dataset}.qa

mkdir -p $qaDir
rm -r $qaDir
mkdir -p $qaDir

for bit in {0..5} 100; do
  groovy qaCut.groovy $dataset false $bit
  qa=$(ls -t outmon.${dataset}/electron_trigger_*QA*.hipo | grep -v epoch | head -n1)
  mv $qa ${qaDir}/$(echo $qa | sed 's/^.*_QA_//g')
done

cp QA/qa.${dataset}/qaTree.json $qaDir
echo ""
cat outdat.${dataset}/passFractions.dat

#!/bin/bash
# after finishing analysis in the `QA` subdirectory, this script will call
# qaCut.groovy on the results

if [ $# -ne 1 ]; then
  echo "USAGE: $0 [dataset]"
  exit
fi
dataset=$1

mkdir -p outmon.${dataset}.qa
rm -r outmon.${dataset}.qa
mkdir -p outmon.${dataset}.qa

for bit in {0..5} 100; do
  groovy qaCut.groovy $dataset false $bit
  qa=$(ls -t outmon.${dataset}/electron_trigger_*QA*.hipo | grep -v epoch | head -n1)
  mv $qa outmon.${dataset}.qa/$(echo $qa | sed 's/^.*_QA_//g')
done

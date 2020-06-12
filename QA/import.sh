#!/bin/bash
# copy qaTree.json from ../outdat.$dataset, so we can start the QA
if [ $# -ne 1 ]; then
  echo "USAGE: $0 [dataset]"
  exit
fi
dataset=$1
mkdir -p qa.${dataset}
rm -r qa.${dataset}
mkdir -p qa.${dataset}
cp -v ../outdat.${dataset}/qaTree.json qa.${dataset}/
rm qa
ln -sv qa.${dataset} qa
run-groovy parseQaTree.groovy
echo ""
echo "imported qaTree.json from $dataset"
echo "view qa/qaTable.dat for human readable version"

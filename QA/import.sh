#!/bin/bash
# copy qaTree.json from ../outdat.$dataset, so we can start the QA
if [ $# -lt 1 ]; then
  echo "USAGE: $0 [dataset] [optional: path to qaTree.json]"
  exit
fi
dataset=$1
mkdir -p qa.${dataset}
rm -r qa.${dataset}
mkdir -p qa.${dataset}
if [ $# -eq 2 ]; then
  qatree=$2;
else
  qatree=../outdat.${dataset}/qaTree.json
fi
cp -v $qatree qa.${dataset}/
rm qa
ln -sv qa.${dataset} qa
run-groovy parseQaTree.groovy
echo ""
echo "imported $qatree to local area, and parsed"
echo "view qa/qaTable.dat for human readable version"

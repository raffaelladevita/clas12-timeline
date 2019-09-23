#!/bin/bash
# build root tree

datfile="outdat/mondata.10000.dat"

> num.tmp
n=$(echo "`cat $datfile|wc -l`/6"|bc)
for i in `seq 1 $n`; do
  for j in {1..6}; do echo $i >> num.tmp; done
done
paste -d' ' num.tmp $datfile > tree.tmp

root -l readTree.C
rm {num,tree}.tmp

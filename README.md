# clas12-timeline

To download,
```
git clone https://github.com/JeffersonLab/clas12-timeline.git
```

## calibration QA

To run,
```
./bin/qa.sh TIMELINE
```
where `TIMELINE` is either the URL, for example,
```
https://clas12mon.jlab.org/rga/pass1/version3/tlsummary
```
or the relative path to the timeline, which for this example would be `rga/pass1/version3`. The output
URL containing QA timelines will be printed at the end of the script output; for this example, it will be
```
https://clas12mon.jlab.org/rga/pass1/version3_qa/tlsummary
```

See [further details](https://github.com/JeffersonLab/clas12-timeline/blob/main/calib-qa/README.md) for more information.

## run based monitoring

To build,
```
./bin/detectors.sh build
```

To run, execute following command,

```
./bin/detectors.sh "run group" "cooking version" "/path/to/monitoring/files/""
```
with the adequte arguments, e.g.)
```
./bin/detectors.sh rgb pass0v25.18 /volatile/clas12/rg-b/offline_monitoring/pass0/v25.18/
```
.

See [further details](https://github.com/Sangbaek/run_based_monitoring/blob/master/README.md) for more information.


# How to submit `clas12_monitoring` to slurm
To submit clas12_monitoring for each run from specified directory one should run these commands, e.g.:
<pre>
ln -s <b>PATH_TO_clas12_monitoring_DIR</b>/target/clas12-monitoring-v*.jar .
<b>./mon12-slurm.sh</b> version_name /volatile/clas12/rg-a/production/Spring19/mon/recon/[0-9][0-9][0-9][0-9][0-9][0-9]
</pre>
* first command: makes a link of clas12_monitoring jar file to the local directory
* second command: **first argument** is the name for slurm jobs
* second command: **the following arguments** are the paths to the run directories with reconstructed hipo files
  you can use wild card or the list of directories
* second command: creates one job for each directory from the arguments list
* the `plotsRUN#` directories from clas12_monitoring are produced in the same directory

## The content of `mon12-slurm.sh` is below:

<pre>
#!/usr/bin/bash

#test if one jar for clas12-monitoring package exists in the directory
[[ ! -e `ls clas12-monitoring*.jar 2>/dev/null` ]] && echo "---- [ERROR] Problem with jar file for clas12_monitoring package --------" && echo && exit

# test if there is a version name
echo $1 | grep -q "/" && echo "---- [ERROR] version name must not contain / -------" && echo && exit

ver=$1
shift
echo "---- slurm job name will be: $ver"

mkdir -p log

for rdir
do

[[ ! -e $rdir ]] && echo "------ [ERROR] the folder $rdir does not exist" && continue

run=`basename $rdir | grep -m1 -o "[0-9][0-9][0-9][0-9][0-9][0-9]"`
[[ -z $run ]] && continue
echo "Submitting the job for run $run"
irun=$((10#$run))

max_num_events=100000000

beam_energy=`python -c "beamlist = [<b>
(3861,5673,10.6), (5674, 5870, 7.546), (5871, 6000, 6.535), (6608, 6783, 10.199),
(11620, 11657, 2.182), (11658, 12283, 10.389), (12389, 12444, 2.182), (12445, 12951, 10.389),
(15013,15490, 5.98636), (15533,15727, 2.07052), (15728,15784, 4.02962), (15787,15884, 5.98636),
(16010, 16079, 2.22), (16084, 1e9, 10.54) </b>]

ret=10.6
for r0,r1,eb in beamlist:
  if $irun>=r0 and $irun<=r1:
    ret=eb
print(ret)
"`
<b>
sbatch << END_OF_SBATCH
#!/bin/sh
#SBATCH --ntasks=1
#SBATCH --job-name=$ver
#SBATCH --output=log/%x-%j-%N.out
#SBATCH --error=log/%x-%j-%N.err
#SBATCH --partition=production
#SBATCH --account=clas12
#SBATCH --mem-per-cpu=1500

source /group/clas12/packages/setup.sh
module load clas12/pro
module switch clas12/3.2

ls $rdir/* > "$irun".input

echo "[shell] >> MAKING DIRECTORY FOR PLOTS FOR SINGLE RUN: $irun with beam energy: $beam_energy"
mkdir plots${irun}/

java -DCLAS12DIR="\${COATJAVA}/" -Xmx1024m -cp "\${COATJAVA}/lib/clas/*:\${COATJAVA}/lib/utils/*:./*" org.jlab.clas12.monitoring.ana_2p2 $irun "$irun".input $max_num_events $beam_energy

END_OF_SBATCH
</b>
done
</pre>

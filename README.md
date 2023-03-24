# clas12-timeline

To download,
```
git clone https://github.com/JeffersonLab/clas12-timeline.git
```


# How to submit `clas12_monitoring` to slurm
To submit clas12_monitoring for each run from specified directory one should run these commands, e.g.:
<pre>
./bin/build-all.sh
<b>./bin/slurm-mon12.sh</b> version_name /volatile/clas12/rg-a/production/Spring19/mon/recon/[0-9][0-9][0-9][0-9][0-9][0-9]
</pre>
* slurm-mon12.sh: **first argument** is the name for slurm jobs
* slurm-mon12.sh: **the following arguments** are the paths to the run directories with reconstructed hipo files
  you can use wild card or the list of directories
* slurm-mon12.sh: creates one job for each directory from the arguments list
* the `plots` directory is created and contains `plots#RUN` directories for each job


##  Timeline
To build,
```
./bin/build-all.sh
```

To run, execute following command,

```
./bin/detectors.sh "run group" "cooking version" "/path/to/monitoring/files/""
```
with the adequte arguments, e.g.)
```
./bin/detectors.sh rgb pass0v25.18 /volatile/clas12/rg-b/offline_monitoring/pass0/v25.18/
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




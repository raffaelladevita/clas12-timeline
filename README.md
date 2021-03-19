# clas12-timeline

To download,
```
git clone --recurse-submodules https://github.com/JeffersonLab/clas12-timeline.git
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

## run_based_monitoring

To run, 
```
./bin/detectors.sh /path/to/dir/containing/monitoring/files
```

See [further details](https://github.com/Sangbaek/run_based_monitoring/blob/master/README.md) for more information.

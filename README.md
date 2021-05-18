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

## run_based_monitoring

To setup,
```
./bin/detectors.sh setup
```


To run, 
Replace /path/to/monitoring/files/ with the directory containing plots, e.g.) /volatile/clas12/rg-b/offline_monitoring/pass0/v25.18/.
Edit the cook version and run group at run.sh for the correct output directory name.

```
./bin/run.sh /path/to/monitoring/files/
```

See [further details](https://github.com/Sangbaek/run_based_monitoring/blob/master/README.md) for more information.

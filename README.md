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

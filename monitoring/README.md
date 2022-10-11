# clas12_monitoring
An independent set of classes for clas12 monitoring

Current active branches in the repo only supports hipo4 and coatjava6.

## How to compile

```
git clone https://github.com/JeffersonLab/clas12_monitoring
cd clas12_monitoring
mvn clean package
```
will create a jar file inside target.

## How to run the monitoring codes

For example, the run 11127 of rg-b can be monitored in following steps.

- ```ls /cache/clas12/rg-b/production/decoded/6.5.6/011127/* > list11127.txt```
- ```mkdir plots11127```
- ```java -DCLAS12DIR="$COATJAVA" -cp "$COATJAVA/lib/clas/*:$COATJAVA/lib/utils/*:target/*" org.jlab.clas12.monitoring.ana_2p2 11127 list11127.txt 100000000 10.4```

The first step is to create the list of files to be monitored, and the second one is to run the compiled codes for monitoring. Change 11127 to the wanted run number. The last two arguments are the maximum event numbers to monitor, and the beam energy (10.4 GeV for example).

The class ana_2p2 runs [all source codes](src/main/java/org/jlab/clas12/monitoring). If it is enough to create updates in a specific detector monitoring, one can run the relevant code only. For example, for DC and TOF monitoring, please use org.jlab.clas12.monitoring.tof_monitoring.

Currently, each detector is related to the following class names.

- All detectors: ana_2p2
- BAND: BAND
- CVT, CTOF: central
- CND: cndCheckPlots
- general DST monitoring: monitor2p2GeV
- rgb LD2 target: deutrontarget
- rgb specific DST monitoring: dst_mon
- FT: FT
- HTCC: HTCC
- LTCC: LTCC
- CVT occupancies: occupancies
- RICH: RICH
- DC, FTOF: tof_monitor

Please consider to run above using the batch farm with Auger or swif workflow in case there is a need for the monitoring service tasks.

# Hipo3 Release
There is a [hipo3 release](https://github.com/JeffersonLab/clas12_monitoring/releases/tag/hipo3)
that contains the latest version as of September 27th, 2019, supporting hipo3.

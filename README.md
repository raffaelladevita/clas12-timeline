# clasqa
Data monitoring tools for CLAS run QA

* There are two data monitoring tools:
  * Electron Trigger and Faraday Cup Monitor: monitors electron trigger and
    determines quality assurance (QA) cuts for filtering 'bad' files
  * General Monitor: generalized data monitor, used to supplement the QA
* The sections below explain each of these monitors;
  [docDiagram.pdf](docDiagram.pdf) shows a flowcharts of the scripts and I/O


## Electron Trigger and Faraday Cup Monitor
* This monitor tracks the electron trigger count, normalized by the Faraday cup charge
* The variable `${dataset}` will be used throughout as a name specifying the data set to
  be analyzed; this name is for organization purposes, for those who want to
  monitor several different sets of data


### Input files: 
* Monitoring histograms from Andrey's data monitor; these files should be stored in or
  symlinked as `monsub.${dataset}/`
* Faraday cup data: this is a `JSON` file containing the Faraday cup (FC) information,
  also produced by Andrey; this should be stored or symlinked as
  `fcdata.${dataset}.json`


### Terse procedure:
* `groovy qaRead.groovy $dataset`
  * (optional: redirect `stderr` and watch for errors, in case there are still bugs)
* `mkTree.sh $dataset`; generate `epochs.${dataset}.txt` manually (see verbose
  procedure below)
* `groovy qaPlot.groovy $dataset` 
* `groovy qaCut.groovy $dataset`


### Verbose procedure:
* `groovy qaRead.groovy $dataset`
  * Reads `monsub.${dataset}/*.hipo` files, scanning for histograms to obtain the number
    of electrons for each sector; one hipo file corresponds to one 5-file
  * Reads `fcdata.${dataset}.json` to obtain the FC data, both ungated and gated, for
    the corresponding run and 5-file
  * Outputs `outdat.${dataset}.hipo`, which is a data table with the following columns:
    * run number
    * 5-file number
    * sector
    * number of electron triggers (`N`)
    * DAQ-gated FC charge at beginning of 5-file (`F_i`)
    * DAQ-gated FC charge at end of 5-file (`F_f`)
    * DAQ-ungated FC charge at beginning of 5-file
    * DAQ-ungated FC charge at end of 5-file
  * The electron trigger QA monitors the ratio `N/F`, where `F=F_f-F_i` 
  * It is recommended to pipe `stderr` somewhere, in case there are still issues; this
    script generates a lot of `stdout` output, and some errors can be easy to miss

* Determine epoch lines
  * At the moment, this step is manual, but could be automated in a future
    release
  * You need to generate `epochs.${dataset}.txt`, which is a list epoch boundary lines
    * Each line should contain two numbers: the first run of the epoch, and the last run
      of the epoch
    * If you do not want to use epoch lines, execute 
      `echo "0 1000000" > epochs.${dataset}.txt`; this is a trick to ensure every run is
      in a single epoch
  * To help determine where to draw the epoch boundaries, execute `mkTree.sh $dataset`
    * this script, in conjunction with `readTree.C`, will build a `ROOT` tree and draw
      N/F vs. file index, along with the current epoch boundary lines (if defined)
      * other plots will be drawn, including N/F vs. run number (as a 2d histogram),
        along with plots for the Faraday Cup
      * the N/F plots are helpful in determining where to put the epoch boundary lines
      * look at N/F and identify where the average value "jumps": this typically
        occurs at the same time for all 6 sectors, but you should check all 6 regardless
      * the decision of where to put the epoch boundary lines is currently done
        manually, but could be automated in a future release

* `groovy qaPlot.groovy $dataset` 
  * reads `outdat.${dataset}.hipo` and generates `outhipo.${dataset}/plots.hipo`
    * within this hipo file, there is one directory for each run, containing several
      plots:
      * `grA*`: N/F vs. file number (the `A` notation is so it appears first in the
        online timeline front-end)
      * `grF*`: F vs. file number
      * `grN*`: N vs. file number
      * `grT*`: livetime vs. file number

* `groovy qaCut.groovy $dataset`
  * reads `outhipo.${dataset}/plots.hipo`, along with `epochs.${dataset}.txt`, to build
    timelines for the online monitor
  * the runs are organized into epochs, wherein each:
    * calculate N/F quartiles
      * `mq`: middle quartile, the overall median N/F
      * `lq`: lower quartile, the median N/F below `mq`
      * `uq`: upper quartile, the median N/F above `mq`
    * QA cut lines are set using an interquartile range (IQR) rule: `cutFactor` * IQR,
      where `cutFactor` adjusts the overall width of the cuts (currently set to `3.0`)
      * the QA cut lines are stored in `cuts.${dataset}.json`
      * this is done for each sector individually
      * if any sector's N/F value is outside the QA cut lines, the file is marked as
        'bad'; othrewise it is marked as 'good'
        * the run number and file number are printed to the files
          `outdat.${dataset}/goodFiles.dat` and `outdat.${dataset}/badFiles.dat`
  * two timelines are generated (which can be uploaded to the webserver):
    * `outhipo.${dataset}/electron_trigger.hipo`
      * timeline is the 'pass fraction': the fraction of files in a run which pass QA
        cuts
      * 6 timelines are plotted simultaneously: one for each sector
      * click any point to show the corresponding graphs and histograms of N/F, N, F,
        and livetime
    * `outhipo.${dataset}/electron_trigger_epochs.hipo`
      * this is a timeline used to evaluate how the QA cuts look overall, for each epoch
      * the timeline is just a list of the 6 sectors; clicking on one of them will show
        plots of N/F, N, F, and livetime, for each epoch
        * the horizontal axis of these plots is a file index, defined as the run
          number plus a small offset (<1) proportional to the file number
      * the N/F plots include the cut lines: here you can zoom in and see how
        well-defined the cut lines are for each epoch
        * if there are any significant 'jumps' in the N/F value, the cut lines may be
          appear to be too wide: this indicates an epoch boundary line needs to be drawn
          at the step in N/F



## General Monitor
* This is a generalized monitor, which accepts DST or skim files, and produces
  monitoring timelines for any quantity of interest
* Particular focus for QA is helicity monitoring

### Input files: 
* DST or skim files
  * DST files are assumed to be organized into directories, with one directory
    corresponding to one run
  * Skim files are assumed to be contained in a single directory, with one skim file
    corresponding to one run
* The file `Tools.groovy` contains several subroutines used by the general monitor
  scripts; depending on your environment, you may need to ensure that this working
  directory is included in the environment variable `$CLASSPATH`
  * One way to do this in `bash` is `export CLASSPATH="${CLASSPATH}:.`, which adds the
    present working directory

### Terse Procedure: 
* `groovy monitorRead.groovy __skim_file__ skim` or
  `groovy monitorRead.groovy __directory_of_DST_files__ dst`
  * This is better run with `slurm` in parallel (details below)
  * The variable `inHipoType` needs to be set manually as the second argument,
    depending on whether you are reading skim files or DST files
* `groovy monitorPlot.groovy`

### Verbose Procedure: 
* `groovy monitorRead.groovy __hipo_file_(directory__ skim(dst)`
  * It is better to run this using `slurm`, but this can be run on a single skim
    file or directory of one run's DST files
    * see the `slurm*.sh` scripts for example job submission scripts
  * the 2nd argument, `inHipoType` needs to be specified so that determination of
    run number and segment(file) number is done correctly
    * use `dst` for DST files
    * use `skim` for skim files
  * the plots are organized into a tree data structure, which allows plot any
    variable, for any set of properties
    * for example, the helicity plots are for pi+,pi-, and positive helicity and
      negative helicity; this is a total of four plots
      * each particle branches into two helicity branches, each of which contain the
        plot object as a leaf
    * there is one plot per 'segment' where a segment is a single DST file
      (5-file) or a set of 10000 events for skim files
      * for skim files, the variable `segmentSize` can be used to change this
        number of events
      * for skim files, the segment number is set to be the average event
        number; the standard deviation is also included, which indicates the
        temporal localization of these events; typically the standard deviation
        is much smaller than the distance between two consecutive segments'
        averege event numbers
      * for DST files, the segment number is set to be the 5-file number, which
        is the minimum file number of the 5 consecutive, concatenated files (and
        is hence divisible by 5)
  * outputs `outmon/monitor*.hipo` files, with one file corresponding to one
    run, along with all the plots of quantities, versus segment number
    * if reading skim files, the points will have horizontal error bars,
      corresponding to the event number standard deviation described above

* `groovy monitorPlot.groovy`
  * this will read `outmon/monitor*.hipo` files and produce several timelines
    * the list of timelines is at the bottom of the script, and is handled by
      the `hipoWrite` closure, which takes two arguments:
      * the name of the timeline, which will be the name of the corresponding
        hipo file, also send to the `outmon/` directory
      * a list of filters, used to access the plots that are added to that
        timeline; all plots which pass those filters will be plotted together in
        a single timeline hipo file
        * these filters are just names of branches, e.g., `['helic','sinPhi']`
          will select on the sin(phiH) helicity monitor
      * some of the timelines have some custom modifications, such as the
        helicity monitors, so that positive and negative helicities are plotted
        together
    * Let X represent a quantity plotted in a timeline; the timeline plots the
      average value of X, versus run number
      * Several variables may be plotted together, on the same timeline plot,
        sharing the same vertical axis numbers, despite possibly having
        different units
        * Units of energy or momentum are GeV
        * Units of angle are radians
      * Clicking on a point will draw several plots below the timeline,
        corresponding to that run:
        * distribution of the average value of X, with one entry per segment
        * graph of the average value of X versus segment number
    * see the supplementary scripts section for some helpful scripts to upload
      timelines to the webserver


## Supplementary Scripts
* `upload.sh __hipoFile(s)__`
  * upload a file to the timeline webserver, via `scp`
  * you may need to alter the webserver location

* `deployMonitor.sh __prefix__`
  * reads `outmon` directory for timeline `hipo` files and copies them to the online
    timeline webserver (you may need to edit the path)
  * if you specify optional argument `prefix`, all timeline hipo files will be
    prepended with that argument, which is helpful to keep the online hipo files
    organized (a useful argument is 'test' :) )

* `deployQA.sh __dataset__  __prefix__`
  * reads `outhipo.${dataset}/` files for the QA timeline hipo files, and copies
    them to the timeline webserver (you may need to edit the path)
  * argument `prefix` tells all timeline hipo files will be
    prepended with that argument, which is helpful to keep the online hipo files

* `indexPage.groovy`
  * generate `ListOfTimelines.json` file, for hipo files in the online directory's
    subirectories

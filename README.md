# clasqa
Data monitoring tools for CLAS run QA

* Tracks the electron trigger count, normalized by the Faraday cup charge
* Also implements helicity monitoring, by tracking inclusive beam spin asymmetries
* Accepts DST or skim files
* [docDiagram.pdf](docDiagram.pdf) shows a flowcharts of the scripts and I/O
* The variable `${dataset}` will be used throughout as a name specifying the data set to
  be analyzed; this name is for organization purposes, for those who want to
  monitor several different sets of data


## PASS1 Procedure
* `exeSlurm.pass1.sh`: runs `monitorRead.groovy` on DSTs using slurm
  * wait for slurm jobs to finish
  * execute `errorPrint.sh` to inspect error logs
* if there are runs you do not want to include in the QA, use `truncate.sh`
  * this is useful to cut out any runs which have not been fully cooked
* `exeTimeline.pass1.sh`, which does the following:
  * runs `monitorPlot.groovy`
  * runs `qaPlot.groovy`
  * runs `qaCut.groovy`
  * copies timelines to webserver
* release timeline to main directory: use `releaseTimelines.sh`


## Procedure and Script Details
* The procedure is outlined by [docDiagram.pdf](docDiagram.pdf); this section details
  the action of each script, in a suggested order of execution

### Input files
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


### DST / Skim reading
First step is to read DST or Skim files, producing hipo files and data tables

* `groovy monitorRead.groovy __hipo_file_(directory)__ skim(dst)`
  * It is better to run this using `slurm`, but this can be run on a single skim file or
    directory of one run's DST files
    * see the `exeSlurm*.sh` scripts for example job submission scripts
  * the 2nd argument, `inHipoType` needs to be specified so that determination of run
    number and segment(file) number is done correctly
    * use `dst` for DST files
    * use `skim` for skim files
  * Outputs:
    * `outdat/data_table_${run}.dat`, which is a data table with the following columns:
      * run number
      * 5-file number
      * sector
      * number of electron triggers (`N`)
      * number of electrons in the forward tagger
      * DAQ-gated FC charge at beginning of 5-file (`F_i`)
      * DAQ-gated FC charge at end of 5-file (`F_f`)
      * DAQ-ungated FC charge at beginning of 5-file
      * DAQ-ungated FC charge at end of 5-file
    * `outmon/monitor_${runnum}.hipo` contains several plots 
      * in the script, they are organized into a tree data structure, which allows plot
        any variable, for any set of properties
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


### Data Organization
It is recommended to copy the data produced in `outdat/` and `outmon/` to a directory
identified with a data set name `$dataset`

* purpose is to keep production of timelines organized
* syntax is `outdat.${dataset}/` and `outmon.${dataset}`
* prodedure (pick one): 
  * use the script `datasetOrganize.sh`, editting it if necessary
  * create symlinks (discouraged, may be buggy)


### Plotting Scripts
These scripts primary purpose is to make plots and store them in HIPO files; these HIPO
files can then be fed to a QA script

* `groovy monitorPlot.groovy $dataset`
  * this will read `outmon.${dataset}/monitor*.hipo` files and produce several timelines
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

* `groovy qaPlot.groovy $dataset [$useFT]` 
  * reads `outdat.${dataset}/data_table.dat` and generates `outmon/monitorElec.hipo`
    * within this hipo file, there is one directory for each run, containing several
      plots:
      * `grA*`: N/F vs. file number (the `A` notation is so it appears first in the
        online timeline front-end)
      * `grF*`: F vs. file number
      * `grN*`: N vs. file number
      * `grT*`: livetime vs. file number
    * if `$useFT` is set, it will use FT electrons instead


### Automated QA of Normalized Electron Yield
This section will run the automated QA of the electron yield; it will ultimately
generate QA timelines, and a `json` file which is used for the manual followup QA

* Determine epoch lines
  * At the moment, this step is manual, but could be automated in a future release
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

* `groovy qaCut.groovy $dataset [$useFT]`
  * reads `outmon/monitorElec.hipo`, along with `epochs.${dataset}.txt`, to build
    timelines for the online monitor
  * if `$useFT` is set, it will use FT electrons instead
  * the runs are organized into epochs, wherein each:
    * calculate N/F quartiles
      * `mq`: middle quartile, the overall median N/F
      * `lq`: lower quartile, the median N/F below `mq`
      * `uq`: upper quartile, the median N/F above `mq`
    * QA cut lines are set using an interquartile range (IQR) rule: `cutFactor` * IQR,
      where `cutFactor` adjusts the overall width of the cuts (currently set to `3.0`)
      * this is done for each sector individually
      * at this point, the automatic QA is initiated; results are stored in
        `outdat.${dataset}/qaTree.json`
  * timelines are generated (which can be uploaded to the webserver):
    * QA timeline
      * timeline is the 'pass fraction': the fraction of files in a run which pass QA
        cuts
      * 6 timelines are plotted simultaneously: one for each sector
      * click any point to show the corresponding graphs and histograms of N/F, N, F,
        and livetime
    * QA timeline "epoch view"
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
    * Several other timelines are generated as well, such as standard deviation of 
      the number of electrons


## Supplementary Scripts
* `deployTimelines.sh __subdirectory__`
  * reads `outmon` directory for timeline `hipo` files and copies them to the online
    timeline webserver (you may need to edit the path), into the specified subdirectory

* `indexPage.groovy`
  * generate `ListOfTimelines.json` file, for hipo files in the online directory's
    subirectories

* `upload.sh __hipoFile(s)__`
  * upload a file to the timeline webserver, via `scp`
  * you may need to alter the webserver location


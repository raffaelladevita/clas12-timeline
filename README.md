# clasqa
Data monitoring tools for CLAS run QA

* There are two data monitoring tools:
  * `QA monitor`: monitors electron trigger and determines quality assurance (QA) cuts
    for filtering 'bad' files
  * `general monitor`: generalized data monitor, used to supplement the QA
* The sections below explain each of these monitors;
  [docDiagram.pdf](docDiagram.pdf) shows a flowcharts of the scripts and I/O

## QA monitor
* The variable `${dataset}` will be used throughout as a name specifying the data set to
  be analyzed; this name is for organization

### Input files: 
* Monitoring histograms from Andrey's data monitor; these files should be stored in or
  symlinked as `monsub.${dataset}/`
* Faraday cup data: this is a `JSON` file containing the Faraday cup (FC) information,
  also produced by Andrey; this should be stored or symlinked as
  `fcdata.${dataset}.json`

### Terse procedure:
* `groovy qaRead.groovy $dataset`
* `mkTree.sh $dataset`; generate `epochs.${dataset}.txt` manually
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

* to be continued... 


## Supplementary Scripts

* `upload.sh`
  * upload a file to the timeline webserver
  * USAGE: `./upload.sh [hipo file(s)]`

* `mkTree.sh`
  * prerequisite: run `qaRead.groovy` to produce the data table
  * this script, in conjuction with `readTree.C`, will build a `ROOT` tree and draw
    `N/F` vs. `file index`, along with the epoch boundary lines
    * the plots are helpful in determining where to put the epoch boundary lines
    * the decision of where to put the epoch boundary lines is currently done manually,
      but could be automated in a future release

* `deploy.sh`
  * reads `outmon` directory for timeline `hipo` files and copies them to the online
    timeline webserver (you may need to edit this script)
  * if you specify an argument, all timeline hipo files will be prepended with that
    argument, which is helpful to keep the online hipo files organized (a useful
    argument is 'test' :) )

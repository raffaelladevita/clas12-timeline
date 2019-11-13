# clasqa
data monitoring tools for CLAS run QA

## Dependencies

* `coatjava`: ensure that `$CLASSPATH` includes `$COATJAVA/lib/clas/*`, where
  `$COATJAVA` points to `coatjava` build
* `monsub/monplots*.hipo` files, which contain data monitoring histograms

## Short Procedure
* `loop_qaElec.sh`
* `groovy cat.groovy`
* `ls -t *.hipo | head -n1 | xargs upload.sh`


## Scripts

* `qaElec.groovy`
  * this script reads a `monsub` hipo file's electron trigger plots to determine number
    of trigger electrons (denoted `N`), along with `fcdata.json` for the Faraday cup
    counts (denoted `F`)
  * USAGE: `groovy qaElec.groovy [run number] [monsub dir] [outputPNG]`
    * the `monsub` directory specifies where the `monplots*.hipo` files are located;
      default `monsub` directory is `../monsub` (also see
      `/volatile/clas12/kenjo/monsub/`)
    * if `outputPNG` is set to 1 (default is 0), then canvases will be drawn and output
      as `png` files to `outpng/`

* `loop_qaElec.sh`
  * this runs `qaElec.groovy` over all hipo files in the specified `monsub` directory; 
    * first it runs over the epochs specified in `epochs.txt`, which determines the
      outlier cuts and stores them in `cuts.json`; each line in `epochs.txt` is the
      start run and end run of each epoch
    * then it runs over all of the runs, using the cuts in `cuts.json` to determine
      which run files are outliers
  * USAGE: `./loop_qaElec.sh [monsub dir] [outputPNG]`
    * the variable `$njobs` specifies the maximum number of jobs to run in parallel
      * if `$njobs` is greater than the number of available threads, `$njobs` will be
        set to that number
      * on `ifarm` interactive nodes with 48 available threads, it's best to keep
        `$njobs` relatively low (e.g., 16) to not interfere with other users
  * output directories:
    * `outhipo`: HIPO files with plots (and hopefully eventually canvases, which are
      plots with the QA cut lines)
    * `outpng`: PNG images of aforementioned canvases (if `outputPNG==1`)
    * `outbad`: list of files with outlier `N/F` values (columns are runnum, filenum,
      list of sectors in which it's an outlier); `outbad/bad.dat` is the full list for
      all runs
    * `outdat`: text dump of `N/F` for each run and file (see `qaElec.groovy` for
      column descriptions)
    * `logfiles`: `stdout` is sent to `logfiles/*.out` and `stderr` to `logfiles/*.err`

* `cat.groovy`
  * read `outhipo` HIPO files, concatenating all of them into a single HIPO file, along
    with a timeline; this output HIPO file is called `outHipoN` in the script, and is
    the file that should be uploaded to the timeline webserver
  * USAGE: `groovy cat.groovy`

* `upload.sh`
  * upload a file to the timeline webserver
  * USAGE: `./upload.sh [hipo file(s)]`

* `cleanup.sh`
  * removes all output files, to prepare for re-run of `loop_qaElec.sh`

* `mkTree.sh`
  * prerequisite: run `qaElec.groovy` on epoch 0 (use run number 10000 to do this); this
    will produce `outdat/mondata.10000.dat`
  * this will build a `ROOT` tree, and then run `readTree.C` on it, which will draw
    `N/F` vs. `file index`, along with the epoch boundary lines

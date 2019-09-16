# clasqa
data monitoring tools for CLAS run QA

## Dependencies

* `coatjava`: ensure that `$CLASSPATH` includes `$COATJAVA/lib/clas/*`, where
  `$COATJAVA` points to `coatjava` build
* `monsub/monplots*.hipo` files, which contain data monitoring histograms


## Scripts

* `qaElec.groovy`
  * usage: `groovy qaElec.groovy [run number] [monsub dir (default=../monsub)]`
    * the `monsub` directory specifies where the `monplots*.hipo` files are located
    * default `monsub` directory is `../monsub` (which I symlink to
      `/volatile/clas12/kenjo/monsub/`)
  * this script reads a `monsub` hipo file's electron trigger plots to determine number
    of trigger electrons (denoted `N`), along with `fcdata.json` for the Faraday cup
    counts (denoted `F`)

* `loop_qaElec.sh`
  * usage: `./loop_qaElec.sh [monsub dir (default=../monsub)]`
  * this runs `qaElec.groovy` over all hipo files in the specified `monsub` directory
  * the variable `$njobs` specifies the maximum number of jobs to run in parallel
    * if `$njobs` is greater than the number of available threads, `$njobs` will be
      set to that number
    * on `ifarm` interactive nodes with 48 available threads, it's best to keep
      `$njobs` relatively low (e.g., 16) to not interfere with other users
  * `stdout` is sent to `logfiles/*.out` and `stderr` to `logfiles/*.err`
  * output directories:
    * `outhipo`: HIPO files with plots (and hopefully eventually canvases, which are
      plots with the QA cut lines)
    * `outpng`: PNG images of aforementioned canvases
    * `outbad`: list of files with outlier `N/F` values (columns are runnum, filenum,
      list of sectors in which it's an outlier); `outbad/bad.dat` is the full list for
      all runs
    * `outdat`: text dump of `N/F` for each run and file (see `qaElec.groovy` for
      column descriptions); `outdat/all.dat` is the concatenation over all runs

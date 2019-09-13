# clasqa
data monitoring tools for CLAS run QA

## Dependencies

* `coatjava`: ensure that `$CLASSPATH` includes `$COATJAVA/lib/clas/*`, where
  `$COATJAVA` points to `coatjava` build
* `monsub/monplots*.hipo` files, which contain data monitoring histograms


## Scripts

* `qaElec.groovy`
  * usage: `groovy qaElec.groovy [monsub hipo file]`
  * reads a `monsub` hipo file's electron trigger plots to determine number of trigger
    electrons, along with `fcdata.json` for the Faraday cup counts

* `loop_qaElec.sh`
  * usage: `./loop_qaElec.sh [(optional) directory of monsub files]`
    * if the `monsub` directory is not specified, it defaults to `../monsub` (which I
      symlink to `/volatile/clas12/kenjo/monsub/`)
    * this runs `qaElec.groovy` over all hipo files in the specified `monsub` directory
    * the variable `$njobs` specifies the maximum number of jobs to run in parallel
      * if `$njobs` is greater than the number of available threads, `$njobs` will be
        set to that number
      * on `ifarm` interactive nodes with 48 available threads, it's best to keep
        `$njobs` relatively low (e.g., 16) to not interfere with other users
    * `stdout` is sent to `logfiles/*.out` and `stderr` to `logfiles/*.err`

# calibration QA

This procedure reads calibration timelines, and checks if certain parameters are within
bounds. The bounds are specified by `cuts.txt` (see below for syntax).

## Usage
- First, set environment variables using `source ../env.sh` (use `bash`)
- Let `path/to/timelines` denote the path to timeline HIPO files you wish to run the QA
  on. This path must be relative to the web directory, `$CLASQAWWW`; if you prefer a
  different directory, edit `$CLASQAWWW`
- Run `qa.sh path/to/timelines`. The URLs for the timelines will be printed upon success

## Testing and Development
- If instead you would like to test the code, especially while developing, add a line to
  `datasetList.txt` (see below for description of this file); it is useful to define
  your own output directory
  - example line for datasetList.txt: 
    `rga_spring19  rga/pass0/v2.2.29  path/to/my/directory`
- Then call `util/runQA.sh [dataset]`

## Files
- `cuts.txt` defines the calibration QA cuts, delimited by spaces, with columns:
  - detector name
  - hipo file name
  - (optional) additional specifier(s)
  - lower bound
  - upper bound
  - units
- `datasetList.txt` is a list of datasets with columns:
  - dataset name, referred to as `dataset` below
  - location of input calibration timelines, relative to `$CLASQAWWW`
  - location of output timelines, relative to `$CLASQAWWW`

## Scripts
- `util/runQA.sh [dataset]` is a wrapper for `util/applyBounds.groovy`
- `util/applyBounds.groovy [dataset]` reads specified input calibration timelines, and
  produces output QA timelines
- `util/url.sh [dataset]` prints URLs for the timelines associated to `dataset`

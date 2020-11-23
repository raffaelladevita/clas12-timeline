# calibration QA

- `datasetList.txt` list with columns:
  - dataset name, referred to as `dataset` below
  - location of input calibration timelines, relative to `$CLASQAWWW`
  - location of output timelines, relative to `$CLASQAWWW`
- `cuts.txt` defines the calibration QA cuts, delimited by spaces, with columns:
  - detector name
  - hipo file name
  - (optional) additional specifier(s)
  - lower bound
  - upper bound
  - units
- `applyBounds.groovy [dataset]` reads specified input calibration timelines,
  and produces output QA timelines

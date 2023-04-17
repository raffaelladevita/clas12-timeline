# QA Timeline Production Flowchart

## Legend
```mermaid
flowchart LR
    data{{Data}}:::data
    timeline{{Timeline<br/>HIPO files}}:::timeline
    exeSlurm[Script automated<br/>by exeSlurm.sh]:::exeSlurm
    exeTimeline[Script automated<br/>by exeTimelines.sh]:::exeTimeline
    manual[Manual step,<br/>not automated]:::manual
    qaScript[Manual QA script,<br/>not automated]:::qaScript

    classDef data fill:#ff8,color:black
    classDef exeSlurm fill:#8f8,color:black
    classDef exeTimeline fill:#bff,color:black
    classDef manual fill:#fbb,color:black
    classDef timeline fill:#8af,color:black
    classDef qaScript fill:#f8f,color:black
```

## Automatic QA Procedure

```mermaid
flowchart TD
    dst{{DSTs}}:::data --> monitorRead[monitorRead.groovy]:::exeSlurm
    monitorRead --> monitorReadOut{{outdat/data_table_$run.dat<br>outmon/monitor_$run.hipo}}:::data
    monitorReadOut --> do[datasetOrganize.sh]:::exeTimeline
    do --> dm{{outmon.$dataset/monitor_$run.hipo}}:::data
    do --> dt{{outdat.$dataset/data_table.dat}}:::data
    
    dm --> monitorPlot[monitorPlot.groovy]:::exeTimeline
    monitorPlot --> tl{{outmon.$dataset/$timeline.hipo}}:::timeline
    
    dt --> qaPlot[qaPlot.groovy]:::exeTimeline
    dt --> man[create/edit<br>epochs.$dataset.txt<br>see mkTree.sh]:::manual
    qaPlot --> monitorElec{{outmon.$dataset/monitorElec.hipo}}:::data
    monitorElec --> qaCut[qaCut.groovy]:::exeTimeline
    man --> qaCut
    qaCut --> tl
    qaCut --> qaTree{{outdat.$dataset/qaTree.json}}:::data
    qaTree --> cd[Manual QA<br/>in QA subdirectory]
    dt --> buildCT[buildChargeTree.groovy]:::exeTimeline
    buildCT --> chargeTree{{outdat.$dataset/chargeTree.json}}:::data
    
    tl --> deploy[deployTimelines.sh]:::exeTimeline
    
    classDef data fill:#ff8,color:black
    classDef exeSlurm fill:#8f8,color:black
    classDef exeTimeline fill:#bff,color:black
    classDef manual fill:#fbb,color:black
    classDef timeline fill:#8af,color:black
    classDef qaScript fill:#f8f,color:black
```

## Manual QA Procedure
- `cd` to the `QA` subdirectory; scripts are run manually here
  - except for `parseQAtree.groovy`, which runs automatically
  - except for `exeQAtimelines.sh`, which is meant to be run as one of the final steps from the top-level directory

```mermaid
flowchart TD
   cd0[cd QA]:::manual-->qaTree
   qaTree{{../outdat.$dataset/qaTree.json}}:::data --> import[import.sh]:::qaScript
    import --> qaLoc{{qa/ -> qa.$dataset/<br>qa/qaTree.json}}:::data
    qaLoc --> parse[parseQAtree.groovy<br>called automatically<br>whenever needed]:::qaScript
    parse --> qaTable{{qa/qaTable.dat}}:::data
    
    qaLoc --> inspect[manual inspection<br>- view qaTable.dat<br>- view online monitor]:::manual
    inspect --> edit{edit?}
    
    edit -->|yes|modify[modify.sh]:::qaScript
    modify --> qaLoc
    modify --> qaBak{{qa.$dataset/qaTree.json.*.bak}}:::data
    qaBak --> undo[if needed, revert<br>modification with<br>undo.sh]:::qaScript
    
    edit -->|no|cd[cd ..]:::qaScript
    cd --> qa[exeQAtimelines.sh]:::qaScript
    qaLoc --> qa
    qa --> qaTL{{outmon.$dataset.qa/$timeline.hipo}}:::timeline
    qa -->|updates|qaTree
    qaTL --> deploy[deployTimelines.sh]:::qaScript
    deploy --> release[releaseTimelines.sh]:::qaScript
    qaTree --> release
    
    classDef data fill:#ff8,color:black
    classDef manual fill:#fbb,color:black
    classDef timeline fill:#8af,color:black
    classDef qaScript fill:#f8f,color:black
```

docDiagram

# Automatic QA Procedure
- yellow hexagons: data
- blue hexagons: timeline HIPO files
- green rectangles: scripts automated by `exeSlurm.sh`
- blue rectangles: scripts automated by `exeTimelines.sh`
- arrows denote input and output of scripts, and show dependencies
```mermaid
graph TD;
    dst{{DSTs}}:::data --> monitorRead[monitorRead.groovy]:::exeSlurm;
    monitorRead --> monitorReadOut{{outdat/data_table_$run.dat<br>outmon/monitor_$run.hipo}}:::data;
    monitorReadOut --> do[datasetOrganize.sh]:::exeTimeline;
    do --> dm{{outmon.$dataset/monitor_$run.hipo}}:::data
    do --> dt{{outdat.$dataset/data_table.dat}}:::data
    
    dm --> monitorPlot[monitorPlot.groovy]:::exeTimeline;
    monitorPlot --> tl{{outmon.$dataset/$timeline.hipo}}:::timeline;
    
    dt --> qaPlot[qaPlot.groovy]:::exeTimeline;
    dt --> man[create/edit<br>epochs.$dataset.txt<br>see mkTree.sh]:::manual;
    qaPlot --> monitorElec{{outmon.$dataset/monitorElec.hipo}}:::data;
    monitorElec --> qaCut[qaCut.groovy]:::exeTimeline;
    man --> qaCut;
    qaCut --> tl;
    qaCut --> qaTree{{outdat.$dataset/qaTree.json}}:::data
    qaTree --> cd[QA subdirectory]
    dt --> buildCT[buildChargeTree.groovy]:::exeTimeline;
    buildCT --> chargeTree{{outdat.$dataset/chargeTree.json}}:::data;
    
    tl --> deploy[deployTimelines.sh]:::exeTimeline;
    
 
    
    
    classDef data fill:#ff8;
    classDef exeSlurm fill:#8f8;
    classDef exeTimeline fill:#bff;
    classDef manual fill:#fbb;
    classDef timeline fill:#8af;
    classDef qa fill:#f8f;
```
# Manual QA
### Note: `cd` to the `QA` subdirectory
- all scripts are run manually here (except `parseQAtree.groovy`, which runs automatically)

```mermaid
graph TD;
   cd0[cd QA]:::manual-->qaTree;
   qaTree{{../outdat.$dataset/qaTree.json}}:::data --> import[import.sh]:::qa;
    import --> qaLoc{{qa/ -> qa.$dataset/<br>qa/qaTree.json}}:::data;
    qaLoc --> parse[parseQAtree.groovy<br>called automatically<br>whenever needed]:::qa;
    parse --> qaTable{{qa/qaTable.dat}}:::data;
    
    qaLoc --> inspect[manual inspection<br>- view qaTable.dat<br>- view online monitor]:::manual;
    inspect --> edit{edit?};
    
    edit -->|yes|modify[modify.sh]:::qa;
    modify --> qaLoc;
    modify --> qaBak{{qa.$dataset/qaTree.json.*.bak}}:::data;
    qaBak --> undo[if needed, revert<br>modification with<br>undo.sh]:::qa;
    
    edit -->|no|cd[cd ..]:::qa;
    cd --> qa[exeQAtimelines.sh]:::qa;
    qaLoc --> qa;
    qa --> qaTL{{outmon.$dataset.qa/$timeline.hipo}}:::timeline;
    qa -->|updates|qaTree;
    qaTL --> deploy[deployTimelines.sh]:::qa
    deploy --> release[releaseTimelines.sh]:::qa
    qaTree --> release
    
    classDef data fill:#ff8;
    classDef manual fill:#fbb;
    classDef timeline fill:#8af;
    classDef qa fill:#f8f;
```
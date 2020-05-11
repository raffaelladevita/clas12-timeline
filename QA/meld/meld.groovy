// combine a new qaTree.json file with an old one
// - this is used if you've made a major update to the QA, and you
//   want to "meld" the new results with old results
// - you need to control which defect bits you want to overwrite:
//   - some bits you will prefer to use the old qaTree.json version
//   - other bits you may prefer to use the new ones
// - this script is "one-time-use", so you need to read it carefully before
//   running it
// - it assumes the file names are `qaTree.json.old` and `qaTree.json.new`
// - the ouptut will be `qaTree.json.melded`
// - be sure to backup any qaTree.json files in case something goes wrong

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import Tools
Tools T = new Tools()

def debug = false

def newFileN = "qaTree.json.new"
def oldFileN = "qaTree.json.old"
def newFile = new File(newFileN)
def oldFile = new File(oldFileN)
def slurper = new JsonSlurper()
def qaTreeNew = slurper.parse(newFile)
def qaTreeOld = slurper.parse(oldFile)
def qaTreeMelded = [:]
def defectListOld
def meldList
def meldListOR
def defectMask
def comment

// loop through new qaTree runs
// run number loop
qaTreeNew.each{ runnum, fileTree ->
  if(debug) println "RUN=$runnum ---------------"
  qaTreeMelded[runnum] = [:]

  // file number loop
  fileTree.each{ filenum, fileQAnew ->
    if(debug) println "\nrun=$runnum file=$filenum"
    qaTreeMelded[runnum][filenum] = [:]
    
    // get QA info from old qaTree
    fileQAold = qaTreeOld[runnum][filenum]

    // get the comment from the old qaTree file
    comment = T.getLeaf(fileQAold,['comment'])
    if(comment==null) comment=""
    qaTreeMelded[runnum][filenum]['comment'] = comment

    // copy event number range from new qaTree file
    qaTreeMelded[runnum][filenum]['evnumMin'] = fileQAnew['evnumMin']
    qaTreeMelded[runnum][filenum]['evnumMax'] = fileQAnew['evnumMax']

    // loop through sectors and meld their defect bits
    meldListOR = []
    qaTreeMelded[runnum][filenum]['sectorDefects'] = [:]
    fileQAnew['sectorDefects'].each{ sector, defectListNew ->

      meldList = []

      // meld new defect bits
      defectListNew.each{ defect ->
        if(defect==T.bit("TotalOutlier")) meldList << defect
        if(defect==T.bit("TerminalOutlier")) meldList << defect
        if(defect==T.bit("MarginalOutlier")) meldList << defect
        if(defect==T.bit("LowLiveTime")) meldList << defect
      }

      // meld old defect bits
      defectListOld = T.getLeaf(fileQAold,['sectorDefects',sector])
      defectListOld.each{ defect ->
        if(defect==T.bit("SectorLoss")) {
          meldList << defect
          meldList.removeAll(T.bit("TotalOutlier"))
          meldList.removeAll(T.bit("TerminalOutlier"))
          meldList.removeAll(T.bit("MarginalOutlier"))
        }
        if(defect==T.bit("Misc")) meldList << defect
      }


      // add this sector's meldList to the OR of each sector's meldList,
      // and to the melded tree
      qaTreeMelded[runnum][filenum]['sectorDefects'][sector] = meldList
      meldListOR += meldList


      if(debug) {
        println "s${sector}"
        println "     new: $defectListNew"
        println "     old: $defectListOld"
        println "  melded: $meldList"
      }
    } // end sector loop

    // compute defect bitmask
    defectMask = 0
    meldListOR.unique().each { defectMask += (0x1<<it) }
    if(debug) {
      println "--> DEFECT: $defectMask = " + T.printBinary(defectMask)
      println "--> comment: $comment"
    }
    qaTreeMelded[runnum][filenum]['defect'] = defectMask

  } // end filenum loop
} // end runnum loop


// output melded qaTree.json
new File("qaTree.json.melded").write(JsonOutput.toJson(qaTreeMelded))
['new','old','melded'].each{
  "groovy ../parseQaTree.groovy qaTree.json.$it".execute()
}
"./prettyPrint.sh".execute()

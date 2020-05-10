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

def newFileN = "qaTree.json.new"
def oldFileN = "qaTree.json.old"
def newFile = new File(newFileN)
def oldFile = new File(oldFileN)
def slurper = new JsonSlurper()
def qaTreeNew = slurper.parse(newFile)
def qaTreeOld = slurper.parse(oldFile)
def defPath

// loop through new file
qaTreeNew.each{ runnum, fileTree ->
  //println "-----------------"
  fileTree.each{ filenum, thisFile ->
    //println "--- $runnum $filenum"
    thisFile.sectorDefects.each{ sector, defectListNew ->
      defPath = [runnum,filenum,'sectorDefects',sector]
      defectListOld = T.getLeaf(qaTreeOld,defPath)
      if(runnum=='5165' && filenum=='900') {
        println "s${sector} new: $defectListNew\n   old: $defectListOld\n"
      }
    }
  }
}
      



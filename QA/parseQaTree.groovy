// parses qa/qaTree.json into human readable format

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import Tools
Tools T = new Tools()

infile="qa/qaTree.json"
outfile="qa/qaTable.dat"
def outfileF = new File(outfile)
def outfileW = outfileF.newWriter(false)

defectBits = [
  "TotalOutlier",
  "TerminalOutlier",
  "MarginalOutlier",
  "SectorLoss",
  "LiveTimeGT1"
]

def slurper = new JsonSlurper()
def jsonFile = new File(infile)
def qaTree = slurper.parse(jsonFile)
def defStr = []
qaTree.sort{a,b -> a.key.toInteger() <=> b.key.toInteger() }.each{
  run, runTree ->
  outfileW << "\nRUN: $run\n"
  runTree.sort{a,b -> a.key.toInteger() <=> b.key.toInteger() }.each{
    file, fileTree ->
    def defect = fileTree.defect
    if(defect>0) {
      //defStr=[run,file,defect,Integer.toBinaryString(defect)]
      defStr = [run,file]
      defectBits.eachWithIndex { str,i ->
        if(defect >> i & 0x1) defStr += " " + str
      }
      if(fileTree.comment!=null) defStr += " :: " + fileTree.comment
      outfileW << defStr.join(' ') << "\n"
    }
  }
}



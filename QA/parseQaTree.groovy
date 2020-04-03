// parses qa/qaTree.json into human readable format

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import Tools
Tools T = new Tools()

infile="qa/qaTree.json"
outfile="qa/qaTable.dat"
def outfileF = new File(outfile)
def outfileW = outfileF.newWriter(false)

def slurper = new JsonSlurper()
def jsonFile = new File(infile)
def qaTree = slurper.parse(jsonFile)
def defStr = []
def printOut = false
qaTree.sort{a,b -> a.key.toInteger() <=> b.key.toInteger() }.each{
  run, runTree ->
  outfileW << "\nRUN: $run\n"
  runTree.sort{a,b -> a.key.toInteger() <=> b.key.toInteger() }.each{
    file, fileTree ->
    def defect = fileTree.defect
    //defStr=[run,file,defect,Integer.toBinaryString(defect)]
    defStr = [run,file]
    printOut = false

    def getSecList = { bitNum ->
      def secList = []
      fileTree.sectorDefects.each{
        if(bitNum in it.value) secList+=it.key
      }
      return secList
    }

    if(defect>0) {
      T.bitNames.eachWithIndex { str,i ->
        if(defect >> i & 0x1) defStr += " " + str + getSecList(i)
      }
      printOut = true
    }
    if(fileTree.comment!=null) {
      if(fileTree.comment.length()>0) {
        defStr += " :: " + fileTree.comment
        printOut = true
      }
    }
    if(printOut) {
      outfileW << defStr.join(' ') << "\n"
      //outfileW << fileTree.sectorDefects << "\n"
    }
  }
}

outfileW.close()

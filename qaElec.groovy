import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import groovy.json.JsonSlurper
import groovy.json.JsonOutput


// parse farady cup data
def fcFileName = "fcdata.json"
def slurp = new JsonSlurper()
def fcFile = new File(fcFileName)


// vars and subroutines
def sectors = 0..<6
def sector = { int i -> i+1 }
int runnumTmp = -1
boolean dwAppend = false
boolean printOutput
def mapRun
def mapRunFiles
def fcVals
def fcMin
def fcMax
def runnum
def filenum
def jprint = { map -> println JsonOutput.prettyPrint(JsonOutput.toJson(map)) }
def errprint = { str -> 
  System.err << "ERROR in run ${runnum}_${filenum}: "+str+"\n" 
  printOutput = false
}


// loop through input HIPO files
println "---- BEGIN READING FILES"
for(fileN in args) {
  println "-- READ: "+fileN

  printOutput = true

  // get run and file numbers
  def fileNtok = fileN.split('/')[-1].tokenize('_.')
  runnum = fileNtok[1].toInteger()
  filenum = fileNtok[2].toInteger()
  //println "fileNtok="+fileNtok+" runnum="+runnum+" filenum="+filenum


  // open hipo file
  TDirectory dir = new TDirectory()
  dir.readFile(fileN)


  // define output datfile and parse faraday cup json 
  if(runnum!=runnumTmp || runnumTmp<0) {
    dwAppend = false
    runnumTmp = runnum
    mapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
    if(!mapRun) throw new Exception("run ${runnum} not found in "+fcFileName);
  } else dwAppend = true
  def datfile = new File("datfiles/mondata."+runnum+".dat")
  def dw = datfile.newWriter(dwAppend)


  // read faraday cup info for this runfile
  if(mapRun) mapRunFiles = mapRun.groupBy{ it.fnum }.get(filenum)
  if(mapRunFiles) fcVals = mapRunFiles.find()."data"."fc"
  if(fcVals) {
    fcMin = fcVals."fcmin"
    fcMax = fcVals."fcmax"
    //println "fcMin="+fcMin+" fcMax="+fcMax
  } else errprint("run not found in "+fcFileName)


  // read electron trigger histograms and number of entries
  def heth = sectors.collect{ dir.getObject('/electron/trigger/heth_'+sector(it)) }
  sectors.each{ if(heth[it]==null) errprint("missing histogram in sector "+sector(it)) }


  // print output to datfile
  if(printOutput) {
    def entries = { int i -> heth[i].integral() }
    sectors.each{ 
      outputdat = [ runnum, filenum, sector(it), entries(it), fcMin, fcMax ] // <-- COLUMNS
      dw << outputdat.join(' ') << '\n'
    }
  }
  dw.close()
  println "--- done"
  //print datfile.text
}



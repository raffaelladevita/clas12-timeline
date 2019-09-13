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
def jprint = { map -> println JsonOutput.prettyPrint(JsonOutput.toJson(map)) }
def mapRun
def mapRunFiles
def fcVals
def fcMin
def fcMax


// loop through input HIPO files
println "---- BEGIN READING FILES"
for(fileN in args) {
  println "-- READ: "+fileN


  // get run and file numbers
  def fileNtok = fileN.split('/')[-1].tokenize('_.')
  def runnum = fileNtok[1].toInteger()
  def filenum = fileNtok[2].toInteger()
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
  } else throw new Exception("run ${runnum}_${filenum} not found in "+fcFileName)


  // read electron trigger histograms and number of entries
  //boolean dirExists = dir.cd("/electron/triggerI/") // catch empty files
  //dir.cd() // TODO - it would be better to check histograms existence, rather than dirs
  //if(dirExists) {
    def heth = sectors.collect{ dir.getObject('/electron/trigger/heth_'+sector(it)) }
    def entries = { int i -> heth[i].integral() }
    sectors.each{ 
      outputdat = [ runnum, filenum, sector(it), entries(it), fcMin, fcMax ] // <-- COLUMNS
      dw << outputdat.join(' ') << '\n'
    }
  //}
  dw.close()
  println "--- done"
  //print datfile.text
}



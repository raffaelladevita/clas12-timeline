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
for(arg in args) {


  // open hipo file
  TDirectory dir = new TDirectory()
  dir.readFile(arg)


  // get run and file numbers
  def fname = arg.split('/')[-1].tokenize('_.')
  def runnum = fname[1].toInteger()
  def filenum = fname[2].toInteger()
  println "fname="+fname
  println "runnum="+runnum
  println "filenum="+filenum


  // define output datfile and read faraday cup info
  if(runnum!=runnumTmp || runnumTmp<0) {

    dwAppend = false
    runnumTmp = runnum

    mapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
    if(mapRun) mapRunFiles = mapRun.groupBy{ it.fnum }.get(filenum)
    if(mapRunFiles) fcVals = mapRunFiles.find()."data"."fc"
    if(fcVals) {
      fcMin = fcVals."fcmin"
      fcMax = fcVals."fcmax"
    } else throw new Exception("run ${runnum}_${filenum} not found in "+fcFileName)
  } else dwAppend = true
  def datfile = new File("datfiles/mondata."+runnum+".dat")
  def dw = datfile.newWriter(dwAppend)
  

  // read electron trigger histograms and number of entries
  def heth = sectors.collect{ dir.getObject('/electron/trigger/heth_'+sector(it)) }
  def entries = { int i -> heth[i].integral() }
  sectors.each{ 
    outputdat = [ runnum, filenum, sector(it), entries(it), fcMin, fcMax ] // <-- COLUMNS
    dw << outputdat.join(' ') << '\n'
  }
  dw.close()
  //print datfile.text
}



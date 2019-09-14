import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import static groovy.io.FileType.FILES


// ARGUMENTS
def runnum
def monsubDir = "../monsub"
if(args.length==0) {
  print "USAGE: groovy qaElec.groovy [run number] "
  println "[monsubDir (default="+monsubDir+")]"
  return
}
else runnum = args[0].toInteger()
if(args.length>=2) monsubDir = args[1]


// vars and subroutines
def sectors = 0..<6
def sec = { int i -> i+1 }
boolean success
def fcMapRunFiles
def fcVals
def fcStart
def fcStop
def fcCounts
def nTrig
def filenum
def jprint = { map -> println JsonOutput.prettyPrint(JsonOutput.toJson(map)) }
def errPrint = { str -> 
  System.err << "ERROR in run ${runnum}_${filenum}: "+str+"\n" 
  success = false
}


// get list of monsub hipo files
def monsubDirObj = new File(monsubDir)
def fileList = []
monsubDirObj.traverse(
  type: groovy.io.FileType.FILES,
  nameFilter: ~/monplots_${runnum}.*\.hipo/ )
{ fileList << monsubDir+"/"+it.getName() }
fileList.sort()
//fileList.each { println it }


// parse farady cup data for this run
def fcFileName = "fcdata.json"
def slurp = new JsonSlurper()
def fcFile = new File(fcFileName)
def fcMapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
if(!fcMapRun) throw new Exception("run ${runnum} not found in "+fcFileName);


// define plot of number of FC-normalized triggers vs. file number
def grET = sectors.collect{
  def gr = new GraphErrors('grET_'+sec(it))
  gr.setTitle("Electron Trigger N/F -- sector "+sec(it))
  gr.setTitleY("N/F")
  gr.setTitleX("file number")
  return gr
}
def minET = sectors.collect {1E10}
def maxET = sectors.collect {0}
def ET


// define output files
def datfile = new File("datfiles/mondata."+runnum+".dat")
def datfileWriter = datfile.newWriter(false)
def outHipo = new TDirectory()


// loop through input hipo files
println "---- BEGIN READING FILES"
fileList.each{ fileN ->
  println "-- READ: "+fileN

  success = true

  // get file number, and double-check run number
  def fileNtok = fileN.split('/')[-1].tokenize('_.')
  def runnumCheck = fileNtok[1].toInteger()
  filenum = fileNtok[2].toInteger()
  if(runnumCheck!=runnum) errPrint("runnum!=runnumCheck (runnumCheck="+runnumCheck+")")
  //println "fileNtok="+fileNtok+" runnum="+runnum+" filenum="+filenum


  // open hipo file
  TDirectory tdir = new TDirectory()
  tdir.readFile(fileN)


  // read faraday cup info for this runfile
  if(fcMapRun) fcMapRunFiles = fcMapRun.groupBy{ it.fnum }.get(filenum)
  if(fcMapRunFiles) fcVals = fcMapRunFiles.find()."data"."fc"
  if(fcVals) {
    fcStart = fcVals."fcmin"
    fcStop = fcVals."fcmax"
    //println "fcStart="+fcStart+" fcStop="+fcStop
  } else errPrint("not found in "+fcFileName)
  fcCounts = fcStop - fcStart
  if(fcCounts<=0) errPrint("fcCounts = ${fcCounts} <= 0")


  // read electron trigger histograms 
  def heth = sectors.collect{ tdir.getObject('/electron/trigger/heth_'+sec(it)) }
  sectors.each{ if(heth[it]==null) errPrint("missing histogram in sector "+sec(it)) }


  // if no errors thrown above, continue analyzing
  if(success) {

    // compute N/F
    nTrig = { int i -> heth[i].integral() }
    ET = sectors.collect { nTrig(it) / fcCounts }

    // fill grET
    sectors.each{ grET[it].addPoint(filenum, ET[it], 0, 0) }
    minET = sectors.collect { Math.min(minET[it],ET[it]) }
    maxET = sectors.collect { Math.max(maxET[it],ET[it]) }

    // output to datfile
    sectors.each{
      datfileWriter << [ 
        runnum, filenum, sec(it), nTrig(it), fcStart, fcStop, ET[it]
      ].join(' ') << '\n'
    }


  } // eo if(success)
} // eo loop over hipo files
println "--- done reading hipo files"




// define histograms
def buf = 0.1
minET*.multiply(1-buf)
maxET*.multiply(1+buf)
def histET = sectors.collect{ 
  new H1F("histET_"+sec(it), "N/F -- sector "+sec(it), 50, minET[it], maxET[it] )
}

// fill histograms
grET.eachWithIndex{ gr, it ->
  gr.getDataSize(0).times{ i -> histET[it].fill(gr.getDataY(i)) }
}


// output plots and finish
outHipo.mkdir("/plots")
outHipo.cd("/plots")
grET.each{ outHipo.addDataSet(it) }
histET.each{ outHipo.addDataSet(it) }
outHipo.writeFile("test.hipo")
datfileWriter.close()
//print datfile.text

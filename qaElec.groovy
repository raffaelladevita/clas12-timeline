import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import org.jlab.groot.data.DataLine
import org.jlab.groot.ui.TCanvas
import org.jlab.groot.graphics.EmbeddedCanvas
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
def minFilenum = 1E6
def maxFilenum = 0
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
def datfile = new File("outdat/mondata."+runnum+".dat")
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

    // set minima and maxima
    minET = sectors.collect { Math.min(minET[it],ET[it]) }
    maxET = sectors.collect { Math.max(maxET[it],ET[it]) }
    minFilenum = filenum < minFilenum ? filenum : minFilenum
    maxFilenum = filenum > maxFilenum ? filenum : maxFilenum

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
minET = minET*.multiply(1-buf)
maxET = maxET*.multiply(1+buf)
def histET = sectors.collect{ 
  new H1F("histET_"+sec(it), "N/F -- sector "+sec(it), 50, minET[it], maxET[it] )
}
histET.each { it.setOptStat("1111100") }


// fill histograms
grET.eachWithIndex { gr, it ->
  gr.getDataSize(0).times { i -> 
    //println "sec"+sec(it)+": "+gr.getDataX(i)+" "+gr.getDataY(i)
    histET[it].fill(gr.getDataY(i))
  }
}


// get mean and rms
def meanET = histET.collect { it.getMean() }
def cutLoET = histET.collect { it.getMean() - it.getRMS() }
def cutHiET = histET.collect { it.getMean() + it.getRMS() }


// calculate median
def median = { GraphErrors gr ->
  def d = []
  gr.getDataSize(0).times { i -> d.add(gr.getDataY(i)) }
  d.sort()
  def m = d.size().intdiv(2)
  d.size() % 2 ? d[m] : (d[m-1]+d[m]) / 2
}
def medianET = grET.collect { median(it) }
println meanET
println medianET


// define lines
minFilenum -= 10
maxFilenum += 10
def lineMeanET = meanET.collect { new DataLine(minFilenum, it, maxFilenum, it) }
def lineMedianET = medianET.collect { new DataLine(minFilenum, it, maxFilenum, it) }
def lineCutLoET = cutLoET.collect { new DataLine(minFilenum, it, maxFilenum, it) }
def lineCutHiET = cutHiET.collect { new DataLine(minFilenum, it, maxFilenum, it) }
lineMeanET.each { it.setLineColor(2) }
lineMedianET.each { it.setLineColor(3) }
lineCutLoET.each { it.setLineColor(4) }
lineCutHiET.each { it.setLineColor(4) }


// define canvases
/*
//def grCanv = sectors.collect { new TCanvas("grCanv_"+sec(it), 800, 800 ) }
def grCanv = sectors.collect { new EmbeddedCanvas() }
//grCanv.each { it.setName("grCanv_"+sec(it)) }
sectors.each {
  grCanv[it].cd(0)
  grCanv[it].draw(grET[it])
  grCanv[it].draw(lineMeanET[it])
}
*/
def grCanv = new TCanvas("grCanv", 800, 800)
//def grCanv = new EmbeddedCanvas()
grCanv.divide(2,3)
sectors.each { 
  grCanv.cd(it)
  grCanv.draw(grET[it])
  grCanv.draw(lineMeanET[it])
  grCanv.draw(lineMedianET[it])
  grCanv.draw(lineCutLoET[it])
  grCanv.draw(lineCutHiET[it])
}



// output plots and finish
outHipo.mkdir("/graphs")
outHipo.cd("/graphs")
grET.each{ outHipo.addDataSet(it) }

outHipo.mkdir("/hists")
outHipo.cd("/hists")
histET.each{ outHipo.addDataSet(it) }

/*
outHipo.mkdir("/canvs")
outHipo.cd("/canvs")
grCanv.eachWithIndex{ c,it -> outHipo.add("c"+sec(it), c) }
*/

def outHipoN = "outhipo/mondata."+runnum+".hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)
datfileWriter.close()
//print datfile.text

import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D
import org.jlab.groot.ui.TCanvas

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import static groovy.io.FileType.FILES


// ARGUMENTS
//----------------------------------------------------------------------------------
def runnum
def monsubDir = "../monsub"
def outputPNG = false
def garbageCollect = true
if(args.length==0) {
  print "USAGE: groovy qaElec.groovy [run number]"
  print " [monsubDir (default="+monsubDir+")]"
  print " [outputPNG (default=0)]"
  print " [garbageCollect (default=0)]"
  print '\n'
  return
}
else runnum = args[0].toInteger()
if(args.length>=2) monsubDir = args[1]
if(args.length>=3) outputPNG = args[2].toInteger() == 1
if(args.length>=4) garbageCollect = args[3].toInteger() == 1
//----------------------------------------------------------------------------------


// OPTIONS
//----------------------------------------------------------------------------------
boolean useOverallCuts = true // use cuts determined from runAll==true execution
                              // for each run individually
//----------------------------------------------------------------------------------



// vars and subroutines
def sectors = 0..<6
def sec = { int i -> i+1 }
boolean success
def fileNtok
def runnumCheck
def runnumTmp = 0
def heth
def fcMapRunFiles
def fcVals
def fcStart
def fcStop
def fcCounts
def nTrig
def filenum
def filenumIT = 0
def filenumITmap = [:]
def runnumITmap = [:]
def filenumDraw
def val
def fn
def rn
def minFilenum = 1E10
def maxFilenum = 0
def errPrint = { str -> 
  System.err << "ERROR in run ${runnum}_${filenum}: "+str+"\n" 
  success = false
}
def pPrint = { str ->
  JsonOutput.prettyPrint(JsonOutput.toJson(str))
}


// if runnum is this number, all runs will be looped over
boolean runAll = runnum==10000
int epoch = runAll ? runnum - 10000 : -1 // aqui


// get list of monsub hipo files
def monsubDirObj = new File(monsubDir)
def fileList = []
def fileFilter = runAll ? ~/monplots_.*\.hipo/ : ~/monplots_${runnum}.*\.hipo/
monsubDirObj.traverse(
  type: groovy.io.FileType.FILES,
  nameFilter: fileFilter )
{ fileList << monsubDir+"/"+it.getName() }
fileList.sort()
//fileList.each { println it }


// parse farady cup data for this run
def fcFileName = "fcdata.json"
def slurp = new JsonSlurper()
def fcFile = new File(fcFileName)
def fcMapRun
if(!runAll) {
  fcMapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
  if(!fcMapRun) throw new Exception("run ${runnum} not found in "+fcFileName);
}


// define plot name prefixes
def grPrefix = "gr_${runnum}_sec"
def histPrefix = "hist_${runnum}_sec"


// define plot of number of FC-normalized triggers vs. file number
def filenumStr = runAll ? "file index" : "file number"
def defineGraph = { name,suffix ->
  sectors.collect {
    def g = new GraphErrors(name+"_"+sec(it)+suffix)
    g.setTitle("Electron Trigger N/F vs. "+filenumStr+" -- sector "+sec(it))
    g.setTitleY("N/F")
    g.setTitleX(filenumStr)
    return g
  }
}
def grNF = defineGraph("grNF","")
def grCleanedNF = defineGraph(grPrefix,"")
def grOutlierNF = defineGraph(grPrefix,":outliers")
grOutlierNF.each { it.setMarkerColor(2) }

def minNF = sectors.collect {1E10}
def maxNF = sectors.collect {0}
def NF


// define output files
def datfile = new File("outdat/mondata."+runnum+".dat")
def datfileWriter = datfile.newWriter(false)
def badfile = new File("outbad/outliers."+runnum+".dat")
def badfileWriter = badfile.newWriter(false)
def outHipo = new TDirectory()
def runnumDir = "/"+runnum
def outHipoN = "outhipo/mondata."+runnum+".hipo"
def pngname = "outpng/qa.${runnum}.png"


// loop through input hipo files
//----------------------------------------------------------------------------------
println "---- BEGIN READING FILES"
TDirectory tdir
fileList.each{ fileN ->
  println "-- READ: "+fileN

  success = true

  // get file number, and double-check run number
  fileNtok = fileN.split('/')[-1].tokenize('_.')
  runnumCheck = fileNtok[1].toInteger()
  filenum = fileNtok[2].toInteger()
  if(runAll) runnum=runnumCheck // change to current runnum, if looping over all runs
  if(runnumCheck!=runnum) errPrint("runnum!=runnumCheck (runnumCheck="+runnumCheck+")")
  //println "fileNtok="+fileNtok+" runnum="+runnum+" filenum="+filenum


  // if looping over all runs, be sure to parse each new run's fcMapRun
  if(runAll && runnum!=runnumTmp) {
    fcMapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
    if(!fcMapRun) throw new Exception("run ${runnum} not found in "+fcFileName);
    runnumTmp = runnum
  }


  // open hipo file
  tdir = new TDirectory()
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
  heth = sectors.collect{ tdir.getObject('/electron/trigger/heth_'+sec(it)) }
  sectors.each{ if(heth[it]==null) errPrint("missing histogram in sector "+sec(it)) }


  // set maps from filenumIT to filenum and runnum (used only if looping over all runs)
  filenumIT += 1
  filenumITmap[filenumIT] = filenum 
  runnumITmap[filenumIT] = runnum

  
  // set filenumDraw, which will be the file number drawn to plots
  filenumDraw = runAll ? filenumIT : filenum


  // if no errors thrown above, continue analyzing
  if(success) {

    // compute N/F
    nTrig = { int i -> heth[i].integral() }
    NF = sectors.collect { nTrig(it) / fcCounts }

    // fill grNF
    sectors.each{ grNF[it].addPoint(filenumDraw, NF[it], 0, 0) }

    // set minima and maxima
    minNF = sectors.collect { Math.min(minNF[it],NF[it]) }
    maxNF = sectors.collect { Math.max(maxNF[it],NF[it]) }
    minFilenum = filenumDraw < minFilenum ? filenumDraw : minFilenum
    maxFilenum = filenumDraw > maxFilenum ? filenumDraw : maxFilenum

    // output to datfile
    sectors.each{
      datfileWriter << [ 
        runnum, filenum, sec(it), nTrig(it), fcStart, fcStop, NF[it]
      ].join(' ') << '\n'
    }

    // force garbage collection (only if garbageCollect==true)
    tdir = null
    if(garbageCollect) System.gc()
    //if(garbageCollect || runAll) System.gc()


  } // eo if(success)
} // eo loop over hipo files
println "--- done reading hipo files"




// QUARTILES
//----------------------------------------------------------------------------------

// subroutine for calculating median of a list
def median = { d ->
  d.sort()
  def m = d.size().intdiv(2)
  d.size() % 2 ? d[m] : (d[m-1]+d[m]) / 2
}

// assemble N/F values into a data structure, called "dataNF":
// a list of 6 lists, one for each sector; each sector's list is of its N/F values
def dataNF = grNF.collect { gr ->
  def d = []
  gr.getDataSize(0).times { i -> d.add(gr.getDataY(i)) }
  return d
}


// determine quartiles
def mqNF = dataNF.collect { median(it) } // mq = middle quartile (overall median)
dataBelowNF = dataNF.withIndex().collect { d,s -> d.findAll{ it < mqNF[s] } }
dataAboveNF = dataNF.withIndex().collect { d,s -> d.findAll{ it > mqNF[s] } }
def lqNF = dataBelowNF.collect { median(it) } // lq = lower quartile
def uqNF = dataAboveNF.collect { median(it) } // uq = upper quartile
def iqrNF = sectors.collect { uqNF[it] - lqNF[it] } // iqr = interquartile range

// print data and quartiles
/*
sectors.each { 
  print "data: "; println dataNF[it]; println "MQ="+mqNF[it]
  print "dataBelow: "; println dataBelowNF[it]; println "LQ="+lqNF[it]
  print "dataAbove: "; println dataAboveNF[it]; println "UQ="+uqNF[it]
}
*/



// OUTLIER DETERMINATION
//----------------------------------------------------------------------------------

// determine outlier cuts via cutFactor*IQR method
def cutFactor = 2.5
def cutLoNF = lqNF.withIndex().collect { q,i -> q - cutFactor * iqrNF[i] }
def cutHiNF = uqNF.withIndex().collect { q,i -> q + cutFactor * iqrNF[i] }


// parse cuts.json
def cutJsonFile = new File("cuts.json")
def epochCutList
def epochInMap
def epochOutMap
def cutMap
if(cutJsonFile.exists()) {
  epochCutList = slurp.parse(cutJsonFile)
  epochInMap = epochCutList.groupBy{it.epoch}.get(epoch).find()
} else {
  epochCutList = []
  epochInMap = null
}


// write cuts to cuts.json
if(runAll) {
  // if this epoch's cuts were found in json file, delete them
  if(epochInMap!=null) epochCutList.removeElement(epochInMap)

  // build map of cuts
  cutMap = sectors.collectEntries { s->
    [ (sec(s)): [
      'lo': cutLoNF[s],
      'hi': cutHiNF[s],
      'mq': mqNF[s],
    ] ]
  }
  epochOutMap = [
    'epoch': epoch,
    'runLB': 2,
    'runUB': 300,
    'cuts': cutMap,
  ]

  // add map to the list of cut maps and output to json
  epochCutList.add(epochOutMap)
  new File("cuts.json").write(JsonOutput.toJson(epochCutList))
  //println pPrint(epochCutList)
}


// read cuts from json
if(!runAll && cutJsonFile.exists() && useOverallCuts) {
  println("---- OVERRIDE CUTS WITH cuts.json !")
  if(epochInMap!=null) cutMap = epochInMap.get('cuts')
  else throw new Exception("epoch not found in cuts.json")
  //println pPrint(cutMap)
  sectors.each { s ->
    def mp = cutMap.get(Integer.toString(sec(s)))
    cutLoNF[s] = mp.get('lo')
    cutHiNF[s] = mp.get('hi')
    mqNF[s] = mp.get('mq')
  }
}


// print cuts
sectors.each { 
  println "SECTOR "+sec(it)+" CUTS: "+cutLoNF[it]+" to "+cutHiNF[it]+
  " (med="+mqNF[it]+")"
}
return


// loop through N/F values, determining which are outliers
def badlist = [:] // filenum -> list of sectors in which N/F was an outlier
grNF.eachWithIndex { gr, it ->
  gr.getDataSize(0).times { i -> 
    val = gr.getDataY(i) // N/F
    fn = gr.getDataX(i).toInteger() // filenum
    if( val < cutLoNF[it] || val > cutHiNF[it] ) {
      grOutlierNF[it].addPoint( fn, val, 0, 0 )
      if(badlist.containsKey(fn)) badlist[fn].add(sec(it))
      else badlist[fn] = [sec(it)]
      //badness = Math.abs( val - mqNF[it] ) / iqrNF[it]
    }
    else grCleanedNF[it].addPoint( fn, val, 0, 0 )
  }
}
println "badlist = "+badlist


// print outliers to outbad file
if(!runAll) {
  badlist.each { fnum, seclist ->
    if(runAll) {
      rn = runnumITmap[fnum]
      fn = filenumITmap[fnum]
    } else {
      rn = runnum
      fn = fnum
    }
    badfileWriter << [ rn, fn, seclist.join(' ') ].join(' ') << '\n'
  }
}



// HISTOGRAMS
//----------------------------------------------------------------------------------

// define histograms
def buf = 0.1
histL = minNF*.multiply(1-buf)
histH = maxNF*.multiply(1+buf)
def defineHist = { name,suffix -> 
  sectors.collect {
    def h = new H1F(
      name+"_"+sec(it)+suffix, "Electron Trigger N/F -- sector "+sec(it),
      50, histL[it], histH[it] 
    )
    h.setOptStat("1111100")
    return h
  }
}
def histNF = defineHist("histNF","")
def histCleanedNF = defineHist(histPrefix,"")
def histOutlierNF = defineHist(histPrefix,":outliers")
histOutlierNF.each { it.setLineColor(2) }


// fill histograms
def fillHist = { graphs,histos ->
  graphs.eachWithIndex { graph,s ->
    graph.getDataSize(0).times { i ->
      histos[s].fill(graph.getDataY(i))
    }
  }
}
fillHist(grNF,histNF)
fillHist(grCleanedNF,histCleanedNF)
fillHist(grOutlierNF,histOutlierNF)


// get means
def meanNF = histNF.collect { it.getMean() }



// PLOTTING
//----------------------------------------------------------------------------------

// determine N/F axis plot ranges
def plotLoNF = cutLoNF.withIndex().collect { c,i -> Math.min(c,minNF[i]) - buf }
def plotHiNF = cutHiNF.withIndex().collect { c,i -> Math.max(c,maxNF[i]) + buf }

// define lines
minFilenum -= 10
maxFilenum += 10
def buildLine = { nums,name -> 
  nums.withIndex().collect { num,s ->
    new F1D(grPrefix+"_"+sec(s)+":"+name,
    Double.toString(num), minFilenum, maxFilenum) 
  }
}
def lineMeanNF = buildLine(meanNF,"mean")
def lineMqNF = buildLine(mqNF,"mq")
def lineLqNF = buildLine(lqNF,"lq")
def lineUqNF = buildLine(uqNF,"uq")
def lineCutLoNF = buildLine(cutLoNF,"cutLo")
def lineCutHiNF = buildLine(cutHiNF,"cutHi")
lineMeanNF.each { it.setLineColor(1) }
lineMqNF.each { it.setLineColor(2) }
lineLqNF.each { it.setLineColor(3) }
lineUqNF.each { it.setLineColor(3) }
lineCutLoNF.each { it.setLineColor(4) }
lineCutHiNF.each { it.setLineColor(4) }



// output plots to hipo file
def writeHipo = { o -> o.each{ outHipo.addDataSet(it) } }
outHipo.mkdir(runnumDir)
outHipo.cd(runnumDir)
writeHipo(grCleanedNF)
writeHipo(grOutlierNF)
//writeHipo(lineMeanNF)
writeHipo(lineMqNF)
//writeHipo(lineLqNF)
//writeHipo(lineUqNF)
writeHipo(lineCutLoNF)
writeHipo(lineCutHiNF)
writeHipo(histCleanedNF)
writeHipo(histOutlierNF)

File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)

// close buffer writers
datfileWriter.close()
badfileWriter.close()
//println datfile.text
println badfile.text


// output plots to PNG
int canvX = 1200
int canvY = 800
def grCanv
if(outputPNG) {
  grCanv = new TCanvas("grCanv", canvX, canvY)

  grCanv.divide(2,3)
  sectors.each { 
    grCanv.getCanvas().getPad(it).getAxisY().setRange(plotLoNF[it],plotHiNF[it])
    grCanv.cd(it)
    grCanv.draw(grNF[it])
    if(grOutlierNF[it].getDataSize(0)>0) grCanv.draw(grOutlierNF[it],"same")
    //grCanv.draw(lineMeanNF[it])
    grCanv.draw(lineMqNF[it],"same")
    //grCanv.draw(lineUqNF[it])
    //grCanv.draw(lineLqNF[it])
    grCanv.draw(lineCutLoNF[it],"same")
    grCanv.draw(lineCutHiNF[it],"same")
  }

  sleep(2000)
  grCanv.save(pngname)
  println "png saved"
  grCanv.dispose()
}

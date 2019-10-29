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
boolean useOverallCuts = true // use cuts determined from runMany==true execution
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


// if runnum == 10000, all runs will be looped over
// if runnum > 10000, all runs in epoch # runnum-10000 will be looped over
// if runnum < 10000, only that specific run will be analyzed, and if QA cuts exist
//                    for that run's epoch, those epoch's cuts will be used
boolean runMany = runnum>=10000


// determine epoch number, runLB, and runUB
int epoch = runMany ? runnum - 10000 : -1
def epochFile = new File("epochs.txt")
if(!(epochFile.exists())) throw new Exception("epochs.txt not found")
epochIT = 0
def runLB = -1
def runUB = -1
def lb
def ub
epochFile.eachLine { line ->
  epochIT += 1
  (lb,ub) = line.tokenize(' ').collect{it.toInteger()}
  if(runMany) {
    if(epoch==0) {
      if(epochIT==1) runLB = lb
      runUB = ub
    }
    else if(epoch>0) {
      if(epoch==epochIT) {
        runLB = lb
        runUB = ub
      }
    }
  }
  else {
    if(epoch<0) {
      if(runnum>=lb && runnum<=ub) {
        epoch = epochIT
        runLB = lb
        runUB = ub
      }
    }
  }
}
if(runLB<0 || runUB<0) throw new Exception("epoch number problem... bad run number?")
println "runnum=$runnum  epoch=$epoch  runLB=$runLB  runUB=$runUB"
      



// get list of monsub hipo files
def monsubDirObj = new File(monsubDir)
def fileList = []
def fileFilter = runMany ? ~/monplots_.*\.hipo/ : ~/monplots_${runnum}.*\.hipo/
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
if(!runMany) {
  fcMapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
  if(!fcMapRun) throw new Exception("run ${runnum} not found in "+fcFileName);
}



// define plot of number of FC-normalized triggers vs. file number
// note: Faraday cup info is expressed as charge in nC
def filenumStr = runMany ? "file index" : "file number"
def defineGraph = { name,ytitle,suffix ->
  sectors.collect {
    def g = new GraphErrors(name+"_"+sec(it)+suffix)
    g.setTitle(ytitle+" vs. "+filenumStr+" -- sector "+sec(it))
    g.setTitleY(ytitle)
    g.setTitleX(filenumStr)
    return g
  }
}
def grNF = defineGraph("grNF","Electron Trigger N/F","")
def grCleanedNF = defineGraph("grNF_${runnum}_sec","Electron Trigger N/F","")
def grOutlierNF = defineGraph("grNF_${runnum}_sec","Electron Trigger N/F",":outliers")
grOutlierNF.each { it.setMarkerColor(2) }

def grN = defineGraph("grN_${runnum}_sec","Number electron trigs N","")
def grCleanedN = defineGraph("grN_${runnum}_sec","Number electron trigs N","")
def grOutlierN = defineGraph("grN_${runnum}_sec","Number electron trigs N",":outliers")
grOutlierN.each { it.setMarkerColor(2) }

def grF = defineGraph("grF_${runnum}_sec","Faraday cup charge F","")
def grCleanedF = defineGraph("grF_${runnum}_sec","Faraday cup charge F","")
def grOutlierF = defineGraph("grF_${runnum}_sec","Faraday cup charge F",":outliers")
grOutlierF.each { it.setMarkerColor(2) }


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
def runnumBak = runnum // backup, (if runnum>=10000, runnum will change in the loop)


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
  if(runMany) runnum=runnumCheck // change to current runnum, if looping over all runs
  if(runnumCheck!=runnum) errPrint("runnum!=runnumCheck (runnumCheck="+runnumCheck+")")
  //println "fileNtok="+fileNtok+" runnum="+runnum+" filenum="+filenum


  // proceed if this run is in the epoch we are analyzing
  if( runnum>=runLB && runnum<=runUB ) {

    // if looping over all runs, be sure to parse each new run's fcMapRun
    if(runMany && runnum!=runnumTmp) {
      fcMapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
      if(!fcMapRun) throw new Exception("run ${runnum} not found in "+fcFileName);
      runnumTmp = runnum
    }


    // open hipo file
    tdir = new TDirectory()
    tdir.readFile(fileN)


    // read faraday cup info for this runfile
    if(fcMapRun) fcMapRunFiles = fcMapRun.groupBy{ it.fnum }.get(filenum)
    //if(fcMapRunFiles) fcVals=fcMapRunFiles.find()."data"."fc" // old fcdata.json
    //if(fcMapRunFiles) fcVals=fcMapRunFiles.find()."data"."fcupgated" // actually ungated
    if(fcMapRunFiles) fcVals=fcMapRunFiles.find()."data"."fcup" // actually gated
    if(fcVals) {
      fcStart = fcVals."min"
      fcStop = fcVals."max"
      //println "fcStart="+fcStart+" fcStop="+fcStop
    } else errPrint("not found in "+fcFileName)
    fcCounts = fcStop - fcStart
    if(fcCounts<=0) errPrint("fcCounts = ${fcCounts} <= 0")


    // read electron trigger histograms 
    heth = sectors.collect{ tdir.getObject('/electron/trigger/heth_'+sec(it)) }
    sectors.each{ if(heth[it]==null) errPrint("missing histogram in sector "+sec(it)) }


    // set maps from filenumIT to filenum and runnum (used only if looping over many runs)
    filenumIT += 1
    filenumITmap[filenumIT] = filenum 
    runnumITmap[filenumIT] = runnum

    
    // set filenumDraw, which will be the file number drawn to plots
    filenumDraw = runMany ? filenumIT : filenum


    // if no errors thrown above, continue analyzing
    if(success) {

      // compute N/F
      nTrig = { int i -> heth[i].integral() }
      NF = sectors.collect { nTrig(it) / fcCounts }

      // fill grNF and grF
      sectors.each{ grN[it].addPoint(filenumDraw, nTrig(it), 0, 0) }
      sectors.each{ grF[it].addPoint(filenumDraw, fcCounts, 0, 0) }
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
      //if(garbageCollect || runMany) System.gc()


    } // eo if(success)
  } // eo if runnum>=runLB && runnum<=runUB
  else println "... not in epoch ${epoch}, skipping"
} // eo loop over hipo files
println "--- done reading hipo files"


if(filenumIT==0) throw new Exception("no files were read!")




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
if(runMany && epoch!=0) {
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
    'runLB': runLB,
    'runUB': runUB,
    'cuts': cutMap,
  ]

  // add map to the list of cut maps and output to json
  epochCutList.add(epochOutMap)
  new File("cuts.json").write(JsonOutput.toJson(epochCutList))
  //println pPrint(epochCutList)
}


// read cuts from json
if(!runMany && cutJsonFile.exists() && useOverallCuts) {
  println("---- OVERRIDE CUTS WITH cuts.json, epoch=${epoch}")
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


// loop through N/F values, determining which are outliers
def badlist = [:] // filenum -> list of sectors in which N/F was an outlier
grNF.eachWithIndex { gr, it ->
  gr.getDataSize(0).times { i -> 
    val = gr.getDataY(i) // N/F
    fn = gr.getDataX(i).toInteger() // filenum
    if( val < cutLoNF[it] || val > cutHiNF[it] ) {
      grOutlierNF[it].addPoint( fn, val, 0, 0 )
      grOutlierN[it].addPoint( fn, grN[it].getDataY(i), 0, 0)
      grOutlierF[it].addPoint( fn, grF[it].getDataY(i), 0, 0)
      if(badlist.containsKey(fn)) badlist[fn].add(sec(it))
      else badlist[fn] = [sec(it)]
      //badness = Math.abs( val - mqNF[it] ) / iqrNF[it]
    }
    else {
      grCleanedNF[it].addPoint( fn, val, 0, 0 )
      grCleanedN[it].addPoint( fn, grN[it].getDataY(i), 0, 0)
      grCleanedF[it].addPoint( fn, grF[it].getDataY(i), 0, 0)
    }
  }
}
println "badlist = "+badlist


// print outliers to outbad file
if(!runMany) {
  badlist.each { fnum, seclist ->
    if(runMany) {
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
      runMany ? 200:50, histL[it], histH[it] 
    )
    h.setOptStat("1111100")
    return h
  }
}
def histNF = defineHist("histNF","")
def histCleanedNF = defineHist("histNF_${runnumBak}_sec","")
def histOutlierNF = defineHist("histNF_${runnumBak}_sec",":outliers")
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
    new F1D("grNF_${runnumBak}_sec_"+sec(s)+":"+name,
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
writeHipo(grCleanedF)
writeHipo(grOutlierF)
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
def grNFcanv
def grNcanv
def grFcanv
if(outputPNG) {
  grNFcanv = new TCanvas("grNFcanv", canvX, canvY)
  grNcanv = new TCanvas("grNcanv", canvX, canvY)
  grFcanv = new TCanvas("grFcanv", canvX, canvY)

  grNFcanv.divide(2,3)
  grNcanv.divide(2,3)
  grFcanv.divide(2,3)

  sectors.each { 
    grNFcanv.getCanvas().getPad(it).getAxisY().setRange(plotLoNF[it],plotHiNF[it])
    grNFcanv.cd(it)
    grNFcanv.draw(grNF[it])
    if(grOutlierNF[it].getDataSize(0)>0) grNFcanv.draw(grOutlierNF[it],"same")
    //grNFcanv.draw(lineMeanNF[it])
    grNFcanv.draw(lineMqNF[it],"same")
    //grNFcanv.draw(lineUqNF[it])
    //grNFcanv.draw(lineLqNF[it])
    grNFcanv.draw(lineCutLoNF[it],"same")
    grNFcanv.draw(lineCutHiNF[it],"same")
  }
  sleep(2000)
  grNFcanv.save("outpng/qa.NF.${runnumBak}.png")
  println "png saved"
  grNFcanv.dispose()

  sectors.each { 
    //grNcanv.getCanvas().getPad(it).getAxisY().setRange(plotLoF[it],plotHiF[it])
    grNcanv.cd(it)
    grNcanv.draw(grN[it])
    if(grOutlierN[it].getDataSize(0)>0) grNcanv.draw(grOutlierN[it],"same")
  }
  sleep(2000)
  grNcanv.save("outpng/qa.N.${runnumBak}.png")
  println "png saved"
  grNcanv.dispose()

  sectors.each { 
    //grFcanv.getCanvas().getPad(it).getAxisY().setRange(plotLoF[it],plotHiF[it])
    grFcanv.cd(it)
    grFcanv.draw(grF[it])
    if(grOutlierF[it].getDataSize(0)>0) grFcanv.draw(grOutlierF[it],"same")
  }
  sleep(2000)
  grFcanv.save("outpng/qa.F.${runnumBak}.png")
  println "png saved"
  grFcanv.dispose()
}

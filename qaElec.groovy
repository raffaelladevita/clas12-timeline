import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import org.jlab.groot.data.DataLine
import org.jlab.groot.ui.TCanvas

import org.jlab.groot.graphics.EmbeddedCanvas
import javax.swing.JFrame
import java.awt.image.BufferedImage
import java.awt.Graphics2D
import javax.imageio.ImageIO 

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import static groovy.io.FileType.FILES


// ARGUMENTS
//----------------------------------------------------------------------------------
def runnum
def monsubDir = "../monsub"
def background = false
if(args.length==0) {
  print "USAGE: groovy qaElec.groovy [run number]"
  print " [monsubDir (default="+monsubDir+")]"
  print " [baground (default=0)]"
  print '\n'
  return
}
else runnum = args[0].toInteger()
if(args.length>=2) monsubDir = args[1]
if(args.length>=3) background = args[2].toInteger() == 1
//----------------------------------------------------------------------------------


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
  gr.setTitle("Run $runnum Electron Trigger N/F -- sector "+sec(it))
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
def badfile = new File("outbad/outliers."+runnum+".dat")
def badfileWriter = badfile.newWriter(false)
def outHipo = new TDirectory()


// loop through input hipo files
//----------------------------------------------------------------------------------
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


// HISTOGRAMS
//----------------------------------------------------------------------------------

// define histograms
def buf = 0.1
histLoET = minET*.multiply(1-buf)
histHiET = maxET*.multiply(1+buf)
def histET = sectors.collect{ 
  new H1F("histET_"+sec(it), "N/F -- sector "+sec(it), 50, histLoET[it], histHiET[it] )
}
histET.each { it.setOptStat("1111100") }

// fill histograms
grET.eachWithIndex { gr, it ->
  gr.getDataSize(0).times { i -> 
    //println "sec"+sec(it)+": "+gr.getDataX(i)+" "+gr.getDataY(i)
    histET[it].fill(gr.getDataY(i))
  }
}

// get means
def meanET = histET.collect { it.getMean() }


// QUARTILES
//----------------------------------------------------------------------------------

// subroutine for calculating median of a list
def median = { d ->
  d.sort()
  def m = d.size().intdiv(2)
  d.size() % 2 ? d[m] : (d[m-1]+d[m]) / 2
}

// assemble N/F values into a data structure, called "dataET":
// a list of 6 lists, one for each sector; each sector's list is of its N/F values
def dataET = grET.collect { gr ->
  def d = []
  gr.getDataSize(0).times { i -> d.add(gr.getDataY(i)) }
  return d
}


// determine quartiles
def mqET = dataET.collect { median(it) } // mq = middle quartile (overall median)
dataBelowET = dataET.withIndex().collect { d,s -> d.findAll{ it < mqET[s] } }
dataAboveET = dataET.withIndex().collect { d,s -> d.findAll{ it > mqET[s] } }
def lqET = dataBelowET.collect { median(it) } // lq = lower quartile
def uqET = dataAboveET.collect { median(it) } // uq = upper quartile
def iqrET = sectors.collect { uqET[it] - lqET[it] } // iqr = interquartile range

// print data and quartiles
/*
sectors.each { 
  print "data: "; println dataET[it]; println "MQ="+mqET[it]
  print "dataBelow: "; println dataBelowET[it]; println "LQ="+lqET[it]
  print "dataAbove: "; println dataAboveET[it]; println "UQ="+uqET[it]
}
*/



// OUTLIER DETERMINATION
//----------------------------------------------------------------------------------

// determine outlier cuts via cutFactor*IQR method
def cutFactor = 2.5
def cutLoET = lqET.withIndex().collect { q,i -> q - cutFactor * iqrET[i] }
def cutHiET = uqET.withIndex().collect { q,i -> q + cutFactor * iqrET[i] }
sectors.each { println "SECTOR "+sec(it)+" CUTS: "+cutLoET[it]+" to "+cutHiET[it] }


// define graph of outliers
def grBadET = sectors.collect{
  def gr = new GraphErrors('grBadET_'+sec(it))
  gr.setTitle("Run $runnum Electron Trigger N/F -- sector "+sec(it))
  gr.setTitleY("N/F")
  gr.setTitleX("file number")
  gr.setMarkerColor(2)
  return gr
}


// loop through N/F values, determining which are outliers
def badlist = [:] // filenum -> list of sectors in which N/F was an outlier
grET.eachWithIndex { gr, it ->
  gr.getDataSize(0).times { i -> 
    def val = gr.getDataY(i) // N/F
    def fn = gr.getDataX(i).toInteger() // filenum
    if( val < cutLoET[it] || val > cutHiET[it] ) {
      grBadET[it].addPoint( fn, val, 0, 0 )
      if(badlist.containsKey(fn)) badlist[fn].add(sec(it))
      else badlist[fn] = [sec(it)]
      //badness = Math.abs( val - mqET[it] ) / iqrET[it]
    }
  }
}
println "badlist = "+badlist


// print outliers to outbad file
badlist.each { fn, seclist ->
  badfileWriter << [ runnum, fn, seclist.join(' ') ].join(' ') << '\n'
}



// PLOTTING
//----------------------------------------------------------------------------------

// determine N/F axis plot ranges
def plotLoET = cutLoET.withIndex().collect { c,i -> Math.min(c,minET[i]) - buf }
def plotHiET = cutHiET.withIndex().collect { c,i -> Math.max(c,maxET[i]) + buf }

// define lines
minFilenum -= 10
maxFilenum += 10
def buildLine = { a -> a.collect { new DataLine(minFilenum, it, maxFilenum, it) } }
def lineMeanET = buildLine(meanET)
def lineMqET = buildLine(mqET)
def lineLqET = buildLine(lqET)
def lineUqET = buildLine(uqET)
def lineCutLoET = buildLine(cutLoET)
def lineCutHiET = buildLine(cutHiET)
lineMeanET.each { it.setLineColor(1) }
lineMqET.each { it.setLineColor(2) }
lineLqET.each { it.setLineColor(3) }
lineUqET.each { it.setLineColor(3) }
lineCutLoET.each { it.setLineColor(4) }
lineCutHiET.each { it.setLineColor(4) }


// define canvases and draw
int canvX = 1200
int canvY = 800

/*
//def grCanv = sectors.collect { new TCanvas("grCanv_"+sec(it), canvX, canvY) }
def grCanv = sectors.collect { new EmbeddedCanvas() }
//grCanv.each { it.setName("grCanv_"+sec(it)) }
sectors.each {
  grCanv[it].cd(0)
  grCanv[it].draw(grET[it])
  grCanv[it].draw(lineMeanET[it])
}
*/

///*
def grCanv = new TCanvas("grCanv", canvX, canvY)
grCanv.setVisible(!background)
//*/
/*
def grFrame = new JFrame("grFrame")
grFrame.setVisible(true)
//grFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
def grCanv = new EmbeddedCanvas()
grFrame.setSize(canvX, canvY)
grFrame.add(grCanv)
*/

grCanv.divide(2,3)
sectors.each { 
  grCanv.getCanvas().getPad(it).getAxisY().setRange(plotLoET[it],plotHiET[it])
  //grCanv.getPad(it).getAxisY().setRange(plotLoET[it],plotHiET[it])
  grCanv.cd(it)
  grCanv.draw(grET[it])
  if(grBadET[it].getDataSize(0)>0) grCanv.draw(grBadET[it],"same")
  //grCanv.draw(lineMeanET[it])
  grCanv.draw(lineMqET[it])
  //grCanv.draw(lineUqET[it])
  //grCanv.draw(lineLqET[it])
  grCanv.draw(lineCutLoET[it])
  grCanv.draw(lineCutHiET[it])
}


// output plots to hipo file
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


// close buffer writers
datfileWriter.close()
badfileWriter.close()
//println datfile.text
println badfile.text


// output canvas
///*
grCanv.save("outpng/qa.${runnum}.png")
if(background) grCanv.dispose()
//*/
/*
BufferedImage grFrameImg = new BufferedImage(
  grFrame.getWidth(), grFrame.getHeight(), BufferedImage.TYPE_INT_RGB);
Graphics2D grFrameGraphics = grFrameImg.createGraphics();
grFrame.printAll(grFrameGraphics);
//grFrameGraphics.dispose();
ImageIO.write(grFrameImg, "png", new File("outpng/qa.${runnum}.png"));
*/

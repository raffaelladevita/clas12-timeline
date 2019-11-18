import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D
import groovy.json.JsonOutput

//----------------------------------------------------------------------------------
// ARGUMENTS: none
//----------------------------------------------------------------------------------

// vars and subroutines
def sectors = 0..<6 
def sec = { int i -> i+1 }
def runnum, sector, epoch
def gr
def pPrint = { str -> JsonOutput.prettyPrint(JsonOutput.toJson(str)) }
def jPrint = { name,object -> new File(name).write(JsonOutput.toJson(object)) }


// read epochs list file
def epochFile = new File("epochs.txt")
if(!(epochFile.exists())) throw new Exception("epochs.txt not found")
def getEpoch = { r,s ->
  def lb,ub
  def e = -1
  epochFile.eachLine { line,i ->
    (lb,ub) = line.tokenize(' ').collect{it.toInteger()}
    if(r>=lb && r<=ub) e=i
  }
  if(e<0) throw new Exception("run $r sector $s has unknown epoch")
  return e
}
  



// open hipo file
def inTdir = new TDirectory()
inTdir.readFile("outhipo/plots.hipo")
def inList = inTdir.getCompositeObjectList(inTdir)

// define 'ratioTree', a tree with the following structure
/* 
  { 
    sector1: {
      epoch1: [ list of N/F values ]
      epoch2: [ list of N/F values ]
    }
    sector2: {
      epoch1: [ list of N/F values ]
      epoch2: [ list of N/F values ]
      epoch3: [ list of N/F values ]
    }
    ...
  }
// - also define 'cutTree', which follows the same structure as 'ratioTree', but
//   with the [ list of N/F values ] replaced with [ cut boundaries (map) ]
// - also define 'epochPlotTree', for plotting N/F within each epoch
*/
def ratioTree = [:]
def cutTree = [:]
def epochPlotTree = [:]

// initialize sector branches
sectors.each{ 
  ratioTree.put(sec(it),[:])
  cutTree.put(sec(it),[:])
  epochPlotTree.put(sec(it),[:])
}

// loop over 'grA' graphs (of N/F vs. filenum), filling ratioTree leaves
inList.each { obj ->
  if(obj.contains("/grA_")) {

    // get runnum and filenum
    (runnum,sector) = obj.tokenize('_').subList(1,3).collect{ it.toInteger() }
    if(sector<1||sector>6) throw new Exception("bad sector number $sector")

    // get epoch num, then initialize epoch branch if needed
    epoch = getEpoch(runnum,sector)
    if(ratioTree[sector][epoch]==null) {
      ratioTree[sector].put(epoch,[])
      cutTree[sector].put(epoch,[:])
      epochPlotTree[sector].put(epoch,new GraphErrors("epoch_${epoch}_sector_${sector}"))
      epochPlotTree[sector][epoch].setTitle(
        "N/F vs. file index -- epoch ${epoch}, sector ${sector}")
    }

    // append N/F values to the list associated to this (sector,epoch)
    gr = inTdir.getObject(obj)
    gr.getDataSize(0).times { i -> ratioTree[sector][epoch].add(gr.getDataY(i)) }
    //ratioTree[sector][epoch].add(runnum) // useful for testing
  }
}
//println pPrint(ratioTree)


// subroutine for calculating median of a list
def median = { d ->
  d.sort()
  def m = d.size().intdiv(2)
  d.size() % 2 ? d[m] : (d[m-1]+d[m]) / 2
}


// establish cut lines using 'cutFactor' x IQR method, and fill cutTree
def cutFactor = 2.5
def mq,lq,uq,iqr,cutLo,cutHi
sectors.each { s ->
  sectorIt = sec(s)
  ratioTree[sectorIt].each { epochIt,ratioList ->

    mq = median(ratioList) // middle quartile
    lq = median(ratioList.findAll{it<mq}) // lower quartile
    uq = median(ratioList.findAll{it>mq}) // upper quartile
    iqr = uq - lq // interquartile range
    cutLo = lq - cutFactor * iqr // lower QA cut boundary
    cutHi = uq + cutFactor * iqr // upper QA cut boundary
    
    cutTree[sectorIt][epochIt]['mq'] = mq
    cutTree[sectorIt][epochIt]['lq'] = lq
    cutTree[sectorIt][epochIt]['uq'] = uq
    cutTree[sectorIt][epochIt]['iqr'] = iqr
    cutTree[sectorIt][epochIt]['cutLo'] = cutLo
    cutTree[sectorIt][epochIt]['cutHi'] = cutHi
  }
}
jPrint("cuts.json",cutTree) // output cutTree to JSON
//println pPrint(cutTree)


// define output hipo file
def outHipoRuns = new TDirectory()
def outHipoEpochs = new TDirectory()

// define timeline graphs
def TL = sectors.collect { s ->
  def g = new GraphErrors("sector_"+sec(s))
  g.setTitle("Electron Trigger QA Pass Fraction")
  g.setTitleY("QA pass fraction")
  g.setTitleX("run number")
  return g
}
def nGood,nBad
    

// vars and subroutines for splitting graphs into "good" and "bad", 
// i.e., "pass QA cuts" and "outside QA cuts", respectively
def grA,grA_good,grA_bad
def grN,grN_good,grN_bad
def grF,grF_good,grF_bad
def grT,grT_good,grT_bad
def copyTitles = { g1,g2 ->
  g2.setTitle(g1.getTitle())
  g2.setTitleX(g1.getTitleX())
  g2.setTitleY(g1.getTitleY())
}
def splitGraph = { g ->
  def gG,gB
  gG = new GraphErrors("t"+g.getName())
  gB = new GraphErrors("t"+g.getName()+":outliers")
  copyTitles(g,gG)
  copyTitles(g,gB)
  gB.setMarkerColor(2)
  return [gG,gB]
}
def copyPoint = { g1,g2,i ->
  g2.addPoint(g1.getDataX(i),g1.getDataY(i),g1.getDataEX(i),g1.getDataEY(i))
}
def addEpochPlotPoint = { plot,val ->
  def n = plot.getDataSize(0) + 1
  plot.addPoint(n,val,0,0)
}

def writeHipo = { hipo,outList -> outList.each{ hipo.addDataSet(it) } }
  
// loop over grA graphs, apply the QA cuts, and fill 'good' and 'bad' graphs
def ratio
inList.each { obj ->
  if(obj.contains("/grA_")) {

    // get runnum, sector, epoch
    (runnum,sector) = obj.tokenize('_').subList(1,3).collect{ it.toInteger() }
    epoch = getEpoch(runnum,sector)

    // split graphs into good and bad
    grA = inTdir.getObject(obj)
    grN = inTdir.getObject(obj.replaceAll("grA","grN"))
    grF = inTdir.getObject(obj.replaceAll("grA","grF"))
    grT = inTdir.getObject(obj.replaceAll("grA","grT"))
    (grA_good,grA_bad) = splitGraph(grA)
    (grN_good,grN_bad) = splitGraph(grN)
    (grF_good,grF_bad) = splitGraph(grF)
    (grT_good,grT_bad) = splitGraph(grT)

    // loop through points in grA and fill good and bad graphs
    grA.getDataSize(0).times { i -> 
      ratio = grA.getDataY(i)
      addEpochPlotPoint(epochPlotTree[sector][epoch],ratio)
      if(ratio > cutTree[sector][epoch]['cutLo'] &&
         ratio < cutTree[sector][epoch]['cutHi']) {
        copyPoint(grA,grA_good,i)
        copyPoint(grN,grN_good,i)
        copyPoint(grF,grF_good,i)
        copyPoint(grT,grT_good,i)
      } else {
        copyPoint(grA,grA_bad,i)
        copyPoint(grN,grN_bad,i)
        copyPoint(grF,grF_bad,i)
        copyPoint(grT,grT_bad,i)
      }
    }

    // write graphs to hipo file
    outHipoRuns.mkdir("/${runnum}")
    outHipoRuns.cd("/${runnum}")
    writeHipo(
      outHipoRuns,
      [
        grA_good,grA_bad,
        grN_good,grN_bad,
        grF_good,grF_bad,
        grT_good,grT_bad
      ]
    )

    /* aqui
    outHipoEpochs.mkdir("/${sector}")
    outHipoEpochs.cd("/${sector}")
    writeHipo(
      outHipoEpochs,
      epochPlotTree[sector].collect{...}
    )
    */

    // add QA passing fraction to timeline graph
    nGood = grA_good.getDataSize(0)
    nBad = grA_bad.getDataSize(0)
    TL[sector-1].addPoint(
      runnum,
      nGood+nBad>0 ? nGood/(nGood+nBad) : 0,
      0,0
    )
  }
}

// write timelines to output hipo file
outHipoRuns.mkdir("/timelines")
outHipoRuns.cd("/timelines")
TL.each { outHipoRuns.addDataSet(it) }

// write hipo file to disk
def outHipoN = "timeline_vsRun.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipoRuns.writeFile(outHipoN)

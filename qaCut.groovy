import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D
import groovy.json.JsonOutput

//----------------------------------------------------------------------------------
// ARGUMENTS:
def dataset = 'fall18'
if(args.length>=1) dataset = args[0]
//----------------------------------------------------------------------------------

// vars and subroutines
def sectors = 0..<6 
def sec = { int i -> i+1 }
def runnum, filenum, sector, epoch
def gr
def pPrint = { str -> JsonOutput.prettyPrint(JsonOutput.toJson(str)) }
def jPrint = { name,object -> new File(name).write(JsonOutput.toJson(object)) }


// read epochs list file
def epochFile = new File("epochs.${dataset}.txt")
if(!(epochFile.exists())) throw new Exception("epochs.${dataset}.txt not found")
def getEpoch = { r,s ->
  //return 1 // (for testing single-epoch mode)
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
inTdir.readFile("outhipo.${dataset}/plots.hipo")
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
//   with the leaves [ list of N/F values ] replaced with [ cut boundaries (map) ]
// - also define 'epochPlotTree', with leaves as maps of plots
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
def (minA,maxA) = [100000,0]
inList.each { obj ->
  if(obj.contains("/grA_")) {

    // get runnum and sector
    (runnum,sector) = obj.tokenize('_').subList(1,3).collect{ it.toInteger() }
    if(sector<1||sector>6) throw new Exception("bad sector number $sector")

    // get epoch num, then initialize epoch branch if needed
    epoch = getEpoch(runnum,sector)
    if(ratioTree[sector][epoch]==null) {
      ratioTree[sector].put(epoch,[])
      cutTree[sector].put(epoch,[:])
      epochPlotTree[sector].put(epoch,[:])
    }

    // append N/F values to the list associated to this (sector,epoch)
    // also determine minimum and maximum values of N/F
    gr = inTdir.getObject(obj)
    gr.getDataSize(0).times { i -> 
      def val = gr.getDataY(i)
      minA = val < minA ? val : minA
      maxA = val > maxA ? val : maxA
      ratioTree[sector][epoch].add(val) 
    }
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
def cutFactor = 3.0
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
jPrint("cuts.${dataset}.json",cutTree) // output cutTree to JSON
//println pPrint(cutTree)


// vars and subroutines for splitting graphs into "good" and "bad", 
// i.e., "pass QA cuts" and "outside QA cuts", respectively
def grA,grA_good,grA_bad
def grN,grN_good,grN_bad
def grF,grF_good,grF_bad
def grT,grT_good,grT_bad
def histA_good, histA_bad
def nGood,nBad
def nGoodTotal = 0
def nBadTotal = 0
def copyTitles = { g1,g2 ->
  g2.setTitle(g1.getTitle())
  g2.setTitleX(g1.getTitleX())
  g2.setTitleY(g1.getTitleY())
}
def copyPoint = { g1,g2,i ->
  g2.addPoint(g1.getDataX(i),g1.getDataY(i),g1.getDataEX(i),g1.getDataEY(i))
}
def splitGraph = { g ->
  def gG,gB
  gG = new GraphErrors(g.getName())
  gB = new GraphErrors(g.getName()+":outliers")
  copyTitles(g,gG)
  copyTitles(g,gB)
  gB.setMarkerColor(2)
  return [gG,gB]
}
  


// define 'epoch plots', which are time-ordered concatenations of all the plots, 
// and put them in the epochPlotTree
def defineEpochPlot = { name,ytitle,s,e ->
  def g = new GraphErrors("${name}_s${s}_e${e}")
  g.setTitle(ytitle+" vs. file index -- sector $s, epoch $e")
  g.setTitleY(ytitle)
  g.setTitleX("file index")
  return splitGraph(g) // returns list of plots ['good','bad']
}
def insertEpochPlot = { map,name,plots ->
  map.put(name+"_good",plots[0])
  map.put(name+"_bad",plots[1])
}
sectors.each { s ->
  sectorIt = sec(s)
  ratioTree[sectorIt].each { epochIt,ratioList ->
    insertEpochPlot(epochPlotTree[sectorIt][epochIt],
      "grA",defineEpochPlot("grA_epoch","Electron Trigger N/F",sectorIt,epochIt))
    insertEpochPlot(epochPlotTree[sectorIt][epochIt],
      "grN",defineEpochPlot("grN_epoch","Number electron trigs N",sectorIt,epochIt))
    insertEpochPlot(epochPlotTree[sectorIt][epochIt],
      "grF",defineEpochPlot("grF_epoch","Faraday cup charge F [nC]",sectorIt,epochIt))
    insertEpochPlot(epochPlotTree[sectorIt][epochIt],
      "grT",defineEpochPlot("grT_epoch","Live Time",sectorIt,epochIt))
  }
}


// define output hipo files and outliers list
def outHipoRuns = new TDirectory()
def outHipoEpochs = new TDirectory()
def outHipoA = new TDirectory()
def outHipoSigmaN = new TDirectory()
def outHipoSigmaF = new TDirectory()
def outHipoRhoNF = new TDirectory()

// define good and bad file lists
def qaTree = [:] // [runnum][filenum][sector] -> "good" or "bad"
def goodFile = new File("outdat.${dataset}/goodFiles.dat")
def goodFileWriter = goodFile.newWriter(false)
def badFile = new File("outdat.${dataset}/badFiles.dat")
def badFileWriter = badFile.newWriter(false)



// define timeline graphs
def TLqa = sectors.collect { s ->
  def g = new GraphErrors("sector_"+sec(s))
  g.setTitle("Electron Trigger QA Pass Fraction")
  g.setTitleY("QA pass fraction")
  g.setTitleX("run number")
  return g
}
def TLqaEpochs = new GraphErrors("epoch_sectors")
TLqaEpochs.setTitle("choose a sector")
TLqaEpochs.setTitleX("sector")
sectors.each{ TLqaEpochs.addPoint(sec(it),1.0,0,0) }
def TLA = sectors.collect { s ->
  def g = new GraphErrors("A_sector_"+sec(s))
  g.setTitle("Number of Electron Triggers N / Faraday Cup Charge F")
  g.setTitleY("N/F")
  g.setTitleX("run number")
  return g
}
def TLsigmaN = sectors.collect { s ->
  def g = new GraphErrors("sigmaN_sector_"+sec(s))
  g.setTitle("relative uncertainty sigmaN/aveN vs. run number")
  g.setTitleY("sigmaN/aveN")
  g.setTitleX("run number")
  return g
}
def TLsigmaF = sectors.collect { s ->
  def g = new GraphErrors("sigmaF_sector_"+sec(s))
  g.setTitle("relative uncertainty sigmaF/aveF vs. run number")
  g.setTitleY("sigmaF/aveF")
  g.setTitleX("run number")
  return g
}
def TLrhoNF = sectors.collect { s ->
  def g = new GraphErrors("rhoNF_sector_"+sec(s))
  g.setTitle("Pearson correlation coefficient rho_{NF} vs. run number")
  g.setTitleY("rho_{NF}")
  g.setTitleX("run number")
  return g
}

    
// other subroutines
def lineMedian, lineCutLo, lineCutHi
def elineMedian, elineCutLo, elineCutHi
def buildLine = { graph,name,val ->
  leftBound = graph.getDataX(0)
  rightBound = graph.getDataX(graph.getDataSize(0)-1)
  new F1D(graph.getName()+":"+name,Double.toString(val),leftBound,rightBound)
}
def addEpochPlotPoint = { plotOut,plotIn,i,r ->
  def f = plotIn.getDataX(i) // filenum
  def n = r + f/5000.0 // "file index"
  plotOut.addPoint(n,plotIn.getDataY(i),0,0)
}
def writeHipo = { hipo,outList -> outList.each{ hipo.addDataSet(it) } }
def addGraphsToHipo = { hipoFile ->
  hipoFile.mkdir("/${runnum}")
  hipoFile.cd("/${runnum}")
  writeHipo(
    hipoFile,
    [
      grA_good,grA_bad,
      grN_good,grN_bad,
      grF_good,grF_bad,
      grT_good,grT_bad,
      histA_good,histA_bad,
      lineMedian, lineCutLo, lineCutHi
    ]
  )
}


// subroutine for projecting a graph onto the y-axis as a histogram
def buildHisto = { graph,nbins,binmin,binmax ->

  // expand histogram range a bit so the projected histogram is padded
  def range = binmax - binmin
  binmin -= 0.05*range
  binmax += 0.05*range

  // set the histogram names and titles
  // assumes the graph name is 'gr._.*' (regex syntax) and names the histogram 'gr.h_.*'
  def histN = graph.getName().replaceAll(/^gr./) { graph.getName().replaceAll(/_.*$/,"h") }
  def histT = graph.getTitle().replaceAll(/vs\..*--/,"distribution --")

  // define histogram and set formatting
  def hist = new H1F(histN,histT,nbins,binmin,binmax)
  hist.setTitleX(graph.getTitleY())
  hist.setLineColor(graph.getMarkerColor())

  // project the graph and return the histogram
  graph.getDataSize(0).times { i -> hist.fill(graph.getDataY(i)) }
  return hist
}


// subroutines for calculating means and variances of lists
def listMean = { valList, wgtList ->
  def numer = 0.0
  def denom = 0.0
  valList.eachWithIndex { val,i ->
    numer += wgtList[i] * val
    denom += wgtList[i]
  }
  return denom>0 ? numer/denom : 0
}
def listCovar = { Alist, Blist, wgtList, muA, muB ->
  def numer = 0.0
  def denom = 0.0
  Alist.size().times { i ->
    numer += wgtList[i] * (Alist[i]-muA) * (Blist[i]-muB)
    denom += wgtList[i]
  }
  return denom>0 ? numer/denom : 0
}
def listVar = { valList, wgtList, mu ->
  return listCovar(valList,valList,wgtList,mu,mu)
}

  
// subroutine to convert a graph into a list of values
def listA, listN, listF, listT, listOne, listWgt
def graph2list = { graph ->
  def lst = []
  graph.getDataSize(0).times { i -> lst.add(graph.getDataY(i)) }
  return lst
}
  
// loop over runs, apply the QA cuts, and fill 'good' and 'bad' graphs
def muN, muF
def varN, varF 
def totN, totF, totA
def reluncN, reluncF
def ratio
def valN,valF,valA
inList.each { obj ->
  if(obj.contains("/grA_")) {

    // get runnum, sector, epoch
    (runnum,sector) = obj.tokenize('_').subList(1,3).collect{ it.toInteger() }
    epoch = getEpoch(runnum,sector)
    if(!qaTree.containsKey(runnum)) qaTree[runnum] = [:]

    // get all the graphs and convert to value lists
    grA = inTdir.getObject(obj)
    grN = inTdir.getObject(obj.replaceAll("grA","grN"))
    grF = inTdir.getObject(obj.replaceAll("grA","grF"))
    grT = inTdir.getObject(obj.replaceAll("grA","grT"))
    listA = graph2list(grA)
    listN = graph2list(grN)
    listF = graph2list(grF)
    listT = graph2list(grT)
    listOne = []
    listA.size().times{listOne<<1}

    // decide whether to enable livetime weighting
    listWgt = listOne // disable
    //listWgt = listT // enable

    // get total, mean, and variance of N and F
    totN = listN.sum()
    totF = listF.sum()
    totA = totN/totF
    muN = listMean(listN,listWgt)
    muF = listMean(listF,listWgt)
    varN = listVar(listN,listWgt,muN)
    varF = listVar(listF,listWgt,muF)

    // calculate Pearson correlation coefficient
    covarNF = listCovar(listN,listF,listWgt,muN,muF)
    corrNF = covarNF / (varN*varF)

    // calculate uncertainties of N and F relative to the mean
    reluncN = Math.sqrt(varN) / muN
    reluncF = Math.sqrt(varF) / muF

    // assign Poisson statistics error bars to graphs of N, F, and N/F
    // - note that N/F error uses Pearson correlation determined from the full run's 
    //   covariance(N,F)
    grA.getDataSize(0).times { i ->
      valN = grN.getDataY(i)
      valF = grF.getDataY(i)
      grN.setError(i,0,Math.sqrt(valN))
      grF.setError(i,0,Math.sqrt(valF))
      grA.setError(i,0,
        (valN/valF) * Math.sqrt(
          1/valN + 1/valF - 2 * corrNF * Math.sqrt(valN*valF) / (valN*valF)
        )
      )
    }
        

    // split graphs into good and bad
    (grA_good,grA_bad) = splitGraph(grA)
    (grN_good,grN_bad) = splitGraph(grN)
    (grF_good,grF_bad) = splitGraph(grF)
    (grT_good,grT_bad) = splitGraph(grT)

    // loop through points in grA and fill good and bad graphs
    grA.getDataSize(0).times { i -> 

      filenum = grA.getDataX(i).toInteger()
      if(!qaTree[runnum].containsKey(filenum)) qaTree[runnum][filenum] = [:]

      ratio = grA.getDataY(i)

      if(ratio > cutTree[sector][epoch]['cutLo'] &&
         ratio < cutTree[sector][epoch]['cutHi']) {
        copyPoint(grA,grA_good,i)
        copyPoint(grN,grN_good,i)
        copyPoint(grF,grF_good,i)
        copyPoint(grT,grT_good,i)
        addEpochPlotPoint(epochPlotTree[sector][epoch]['grA_good'],grA,i,runnum)
        addEpochPlotPoint(epochPlotTree[sector][epoch]['grN_good'],grN,i,runnum)
        addEpochPlotPoint(epochPlotTree[sector][epoch]['grF_good'],grF,i,runnum)
        addEpochPlotPoint(epochPlotTree[sector][epoch]['grT_good'],grT,i,runnum)
        qaTree[runnum][filenum][sector] = "good"
      } else {
        copyPoint(grA,grA_bad,i)
        copyPoint(grN,grN_bad,i)
        copyPoint(grF,grF_bad,i)
        copyPoint(grT,grT_bad,i)
        addEpochPlotPoint(epochPlotTree[sector][epoch]['grA_bad'],grA,i,runnum)
        addEpochPlotPoint(epochPlotTree[sector][epoch]['grN_bad'],grN,i,runnum)
        addEpochPlotPoint(epochPlotTree[sector][epoch]['grF_bad'],grF,i,runnum)
        addEpochPlotPoint(epochPlotTree[sector][epoch]['grT_bad'],grT,i,runnum)
        qaTree[runnum][filenum][sector] = "bad"
      }
    }

    // fill histograms
    histA_good = buildHisto(grA_good,250,minA,maxA)
    histA_bad = buildHisto(grA_bad,250,minA,maxA)

    // define lines
    lineMedian = buildLine(grA,"median",cutTree[sector][epoch]['mq'])
    lineCutLo = buildLine(grA,"cutLo",cutTree[sector][epoch]['cutLo'])
    lineCutHi = buildLine(grA,"cutHi",cutTree[sector][epoch]['cutHi'])

    // write graphs to hipo file
    addGraphsToHipo(outHipoRuns)
    addGraphsToHipo(outHipoA)
    addGraphsToHipo(outHipoSigmaN)
    addGraphsToHipo(outHipoSigmaF)
    addGraphsToHipo(outHipoRhoNF)

    // fill timeline points
    nGood = grA_good.getDataSize(0)
    nBad = grA_bad.getDataSize(0)
    nGoodTotal += nGood
    nBadTotal += nBad
    TLqa[sector-1].addPoint(
      runnum,
      nGood+nBad>0 ? nGood/(nGood+nBad) : 0,
      0,0
    )
    TLA[sector-1].addPoint(runnum,totA,0,0)
    TLsigmaN[sector-1].addPoint(runnum,reluncN,0,0)
    TLsigmaF[sector-1].addPoint(runnum,reluncF,0,0)
    TLrhoNF[sector-1].addPoint(runnum,corrNF,0,0)
  }
}


// write epoch plots to hipo file
sectors.each { s ->
  sectorIt = sec(s)
  outHipoEpochs.mkdir("/${sectorIt}")
  outHipoEpochs.cd("/${sectorIt}")
  epochPlotTree[sectorIt].each { epochIt,map ->

    elineMedian = buildLine(map['grA_good'],"median",cutTree[sectorIt][epochIt]['mq'])
    elineCutLo = buildLine(map['grA_good'],"cutLo",cutTree[sectorIt][epochIt]['cutLo'])
    elineCutHi = buildLine(map['grA_good'],"cutHi",cutTree[sectorIt][epochIt]['cutHi'])

    histA_good = buildHisto(map['grA_good'],500,minA,maxA)
    histA_bad = buildHisto(map['grA_bad'],500,minA,maxA)

    writeHipo(outHipoEpochs,map.values())
    writeHipo(outHipoEpochs,[histA_good,histA_bad])
    writeHipo(outHipoEpochs,[elineMedian,elineCutLo,elineCutHi])
  }
}



// write timelines to output hipo file
outHipoRuns.mkdir("/timelines")
outHipoRuns.cd("/timelines")
TLqa.each { outHipoRuns.addDataSet(it) }
outHipoEpochs.mkdir("/timelines")
outHipoEpochs.cd("/timelines")
outHipoEpochs.addDataSet(TLqaEpochs)
outHipoA.mkdir("/timelines")
outHipoA.cd("/timelines")
TLA.each { outHipoA.addDataSet(it) }
outHipoSigmaN.mkdir("/timelines")
outHipoSigmaN.cd("/timelines")
TLsigmaN.each { outHipoSigmaN.addDataSet(it) }
outHipoSigmaF.mkdir("/timelines")
outHipoSigmaF.cd("/timelines")
TLsigmaF.each { outHipoSigmaF.addDataSet(it) }
outHipoRhoNF.mkdir("/timelines")
outHipoRhoNF.cd("/timelines")
TLrhoNF.each { outHipoRhoNF.addDataSet(it) }


// write hipo files to disk
def outHipoN 

outHipoN = "outhipo.${dataset}/QA_electron_trigger.hipo"
File outHipoRunsFile = new File(outHipoN)
if(outHipoRunsFile.exists()) outHipoRunsFile.delete()
outHipoRuns.writeFile(outHipoN)

outHipoN = "outhipo.${dataset}/QA_electron_trigger_epochs.hipo"
File outHipoEpochsFile = new File(outHipoN)
if(outHipoEpochsFile.exists()) outHipoEpochsFile.delete()
outHipoEpochs.writeFile(outHipoN)

outHipoN = "outhipo.${dataset}/normalized_electron_trigger.hipo"
File outHipoAfile = new File(outHipoN)
if(outHipoAfile.exists()) outHipoAfile.delete()
outHipoA.writeFile(outHipoN)

outHipoN = "outhipo.${dataset}/sigma_N.hipo"
File outHipoSigmaNfile = new File(outHipoN)
if(outHipoSigmaNfile.exists()) outHipoSigmaNfile.delete()
outHipoSigmaN.writeFile(outHipoN)

outHipoN = "outhipo.${dataset}/sigma_F.hipo"
File outHipoSigmaFfile = new File(outHipoN)
if(outHipoSigmaFfile.exists()) outHipoSigmaFfile.delete()
outHipoSigmaF.writeFile(outHipoN)

outHipoN = "outhipo.${dataset}/rho_NF.hipo"
File outHipoRhoNFfile = new File(outHipoN)
if(outHipoRhoNFfile.exists()) outHipoRhoNFfile.delete()
outHipoRhoNF.writeFile(outHipoN)


// sort qaTree and output to json file
qaTree.each { qaRun, qaRunTree ->
  qaRunTree.each { qaFile, qaFileTree -> qaFileTree.sort() }
  qaRunTree.sort()
}
qaTree.sort()
new File("outdat.${dataset}/qaTree.json").write(JsonOutput.toJson(qaTree))

// write goodFile and badFile: lists of good and bad files
def nbad
qaTree.each { qaRun, qaRunTree ->
  qaRunTree.each { qaFile, qaFileTree -> 
    nbad = 0
    qaFileTree.each { sectorNum, qaStr -> if(qaStr=="bad") nbad++ }
    ( nbad>0 ? badFileWriter:goodFileWriter) << [ qaRun, qaFile ].join(' ') << '\n'
  }
}
goodFileWriter.close()
badFileWriter.close()

// print total QA passing fractions
def PF = nGoodTotal / (nGoodTotal+nBadTotal)
println "\n QA cut overall passing fraction: $PF"

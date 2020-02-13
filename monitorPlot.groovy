// reads outmon/monitor* files and generates timeline hipo files
// - to be executed after monitorRead.groovy

import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import java.lang.Math.*
import Tools
Tools T = new Tools()


// get list of input hipo files
def inDir = "outmon"
def inDirObj = new File(inDir)
def inList = []
def inFilter = ~/monitor_.*\.hipo/
inDirObj.traverse( type: groovy.io.FileType.FILES, nameFilter: inFilter ) {
  if(it.size()>0) inList << inDir+"/"+it.getName()
}
inList.sort()
inList.each { println it }

// input hipo files contain a set of distributions for each segment
// this program accumulates these segments' distributions into 'monitor' distributions:
// - let 'X' denote a kinematic variable, plotted as one of these distributions
// - moninotors include: 
//   - average X vs. segment number
//   - distribution of average X
//   - 2D distribution of X vs. segment number

// subroutine to transform an object name to a monitor name
def objToMonName = { name ->
  // strip segment number (and standard dev)
  def tok = name.tokenize('_')
  name = tok[0..-3].join('_')
  // plot helicity states together
  if(name.contains('_hp_')) name = name.replaceAll('_hp_','_')
  else if(name.contains('_hm_')) {
    name = name.replaceAll('_hm_','_')
    name += ":hm"
  }
  return name
}

// subroutine to transform an object title into a monitor title
def objToMonTitle = { title ->
  title = title.replaceAll(/ segment=.*$/,'')
  return title
}

//---------------------------------
// monitor builders
//---------------------------------

// append legend for helicity (as needed)
def appendLegend = { t ->
  if(t.contains('hel+') || t.contains('hel-')) {
    t = t.replaceAll(/ hel. /,' ')
    t += '   (  black = hel+   red = hel-  )'
  }
  return t
}

// build monitor 'average X vs. segment number'
def buildMonAveGr = { tObj ->
  def grN = objToMonName(tObj.getName())
  def grT = objToMonTitle(tObj.getTitle())
  grN = "aveGr_" + grN
  grT = "average " + grT
  grT = grT.replaceAll('::','vs. segment number ::')
  grT = appendLegend(grT)
  def gr = new GraphErrors(grN)
  gr.setTitle(grT)
  if(grN.contains(":hm")) { gr.setMarkerColor(2); gr.setLineColor(2); }
  return gr
}

// build monitor 'average X' distribution
def buildMonAveDist = { tObj,nb,lb,ub ->
  def histN = objToMonName(tObj.getName())
  def histT = objToMonTitle(tObj.getTitle())
  histN = "aveDist_" + histN
  histT = "average " + histT
  histT = histT.replaceAll('::','distribution ::')
  histT = appendLegend(histT)
  def hist = new H1F(histN,histT,nb,lb,ub)
  if(histN.contains(":hm")) { hist.setLineColor(2); }
  return hist
}



//-----------------------------------------
// fill the monitors
//-----------------------------------------

def monTree = [:]

def inTdir = new TDirectory()
def objList
def part,hel
def runnum,segnum
def tok
def obj
def aveX
def aveXerr
def helDef,helUndef,defFrac

// loop over sinphi hipo files
inList.each { inFile ->
  inTdir.readFile(inFile)
  objList = inTdir.getCompositeObjectList(inTdir)
  objList.each { objN ->
    obj = inTdir.getObject(objN)

    // tokenize object name name to get runnum, segment number & deviation
    tok = objN.tokenize('/')[-1].tokenize('_')
    runnum = tok[-3].toInteger()
    segnum = new BigInteger(tok[-2])
    segnumDev = new BigInteger(tok[-1])

    // <sinPhi> monitors
    //------------------------------------
    if(objN.contains("/helic_sinPhi_")) {
      part = tok[2]
      hel = tok[3]

      T.addLeaf(monTree,[runnum,'helic','sinPhi',part,hel,'aveGr'],{
        buildMonAveGr(obj)
      })
      T.addLeaf(monTree,[runnum,'helic','sinPhi',part,hel,'aveDist'],{
        buildMonAveDist(obj,100,-0.25,0.25)
      })

      // add <sinPhi> point to the monitors
      if(obj.integral()>0) {
        aveX = obj.getMean()
        aveXerr = 0 // 1.0/Math.sqrt(obj.getIntegral()
        monTree[runnum]['helic']['sinPhi'][part][hel]['aveGr'].addPoint(
          segnum, aveX, segnumDev, aveXerr )
        monTree[runnum]['helic']['sinPhi'][part][hel]['aveDist'].fill(aveX)

      }
    }

    // helicity distribution monitor
    //------------------------------------
    if(objN.contains("/helic_dist_")) {

      // fraction of events with a defined helicity
      T.addLeaf(monTree,[runnum,'helic','dist','definedFracNumer'],{0})
      T.addLeaf(monTree,[runnum,'helic','dist','definedFracDenom'],{0})
      T.addLeaf(monTree,[runnum,'helic','dist','definedFracGr'],{
        def g = buildMonAveGr(obj)
        def gN = g.getName().replaceAll(/^aveGr_/,'definedFracGr_')
        def gT = g.getTitle().replaceAll(
          /^.*::/,'defined helicity fraction vs. segment number ::')
        g.setName(gN)
        g.setTitle(gT)
        return g
      })
      T.addLeaf(monTree,[runnum,'helic','dist','definedFracDist'],{
        def h = buildMonAveDist(obj,50,0,1)
        def hN = h.getName().replaceAll(/^aveDist_/,'definedFracDist_')
        def hT = h.getTitle().replaceAll(
          /^.*::/,'defined helicity fraction distribution ::')
        h.setName(hN)
        h.setTitle(hT)
        return h
      })
      if(obj.integral()>0) {
        helDef = obj.getBinContent(0) + obj.getBinContent(2)
        helUndef = obj.getBinContent(1)
        def numer = helDef
        def denom = helDef + helUndef
        helFrac = numer / denom
        monTree[runnum]['helic']['dist']['definedFracGr'].addPoint(
          segnum, helFrac, segnumDev, 0 )
        monTree[runnum]['helic']['dist']['definedFracDist'].fill(helFrac)
        monTree[runnum]['helic']['dist']['definedFracNumer'] += numer
        monTree[runnum]['helic']['dist']['definedFracDenom'] += denom
      }

    }


  }

  inFile = null // "close" the file
}


//---------------------
// build timelines
//---------------------

// loop through 'aveDist' monitors: for each one, add its mean to the timeline
def timelineTree = [:]
T.exeLeaves(monTree,{
  if(T.key=='aveDist' || T.key=='definedFracGr') {
    // initialise new timeline graph, if not yet initialised
    def tlRun = T.leafPath[0]
    def tlPath = T.leafPath[1..-2]
    T.addLeaf(timelineTree,tlPath,{
      def tlN = tlPath.join('_')
      def tlT = T.leaf.getTitle().replaceAll(/::.*$/,'')
      if(T.key=='aveDist') tlT = tlT.tokenize(' ')[0..1].join(' ')
      else if(T.key=='definedFracGr') tlT = "defined helicity fraction"
      tlT += " vs. run number"
      def tl = new GraphErrors(tlN)
      tl.setTitle(tlT)
      return tl
    })
    // add this run's <X> to the timeline
    if(T.key=='aveDist') {
      aveX = T.leaf.getMean()
      T.getLeaf(timelineTree,tlPath).addPoint(tlRun,aveX,0,0)
    }
    // or if it's a helicity distribution monitor, add the run's overall fractions
    if(T.key=='definedFracGr') {
      T.getLeaf(timelineTree,tlPath).addPoint(
        tlRun,
        monTree[tlRun]['helic']['dist']['definedFracNumer'] /
        monTree[tlRun]['helic']['dist']['definedFracDenom'],
        0,0)
    }
  }
})
    

// subroutines to output timelines and associated plots to a hipo file
def checkFilter( list, filter, keyName="" ) {
  return list.intersect(filter).size() == filter.size() &&
         !keyName.contains("Numer") && !keyName.contains("Denom")
}

def hipoWrite = { hipoName, filterList ->
  def outHipo = new TDirectory()
  monTree.each { run,tree ->
    outHipo.mkdir("/${run}")
    outHipo.cd("/${run}")
    T.exeLeaves(tree,{
      if(checkFilter(T.leafPath,filterList,T.key)) outHipo.addDataSet(T.leaf)
    })
  }
  outHipo.mkdir("/timelines")
  outHipo.cd("/timelines")
  T.exeLeaves(timelineTree,{
    if(checkFilter(T.leafPath,filterList)) outHipo.addDataSet(T.leaf)
  })

  def outHipoN = "outmon/${hipoName}.hipo"
  File outHipoFile = new File(outHipoN)
  if(outHipoFile.exists()) outHipoFile.delete()
  outHipo.writeFile(outHipoN)
}

// write objects to hipo files
hipoWrite("sinPhi",['helic','sinPhi'])
hipoWrite("defined_helicity_fraction",['helic','dist'])




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
  grN = grN + "_aveGr"
  grT = "average " + grT
  grT = grT.replaceAll('::',' vs. segment number ::')
  grT = appendLegend(grT)
  def gr = new GraphErrors(grN)
  gr.setTitle(grT)
  if(grN.contains("_hm_")) { gr.setMarkerColor(2); gr.setLineColor(2); }
  return gr
}

// build monitor 'average X' distribution
def buildMonAveDist = { tObj,nb,lb,ub ->
  def histN = objToMonName(tObj.getName())
  def histT = objToMonTitle(tObj.getTitle())
  histN = histN + "_aveDist"
  histT = "average " + histT
  histT = histT.replaceAll('::',' distribution ::')
  histT = appendLegend(histT)
  def hist = new H1F(histN,histT,nb,lb,ub)
  if(histN.contains("_hm_")) { hist.setLineColor(2); }
  return hist
}



//-----------------------------------------
// fill the monitors
//-----------------------------------------

def monTree = [:]

def inTdir = new TDirectory()
def objList
def part,hel
def var
def varNB
def varLB,varUB
def runnum,segnum
def tok
def obj
def aveX
def aveXerr
def helP,helM,helDef,helUndef,helFrac,rellum

// loop over sinphi hipo files
inList.each { inFile ->
  inTdir.readFile(inFile)
  objList = inTdir.getCompositeObjectList(inTdir)
  objList.each { objN ->
    obj = inTdir.getObject(objN)

    // tokenize object name to get runnum, segment number & deviation
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
      T.addLeaf(monTree,[runnum,'helic','dist','heldef','heldefNumer'],{0})
      T.addLeaf(monTree,[runnum,'helic','dist','heldef','heldefDenom'],{0})
      T.addLeaf(monTree,[runnum,'helic','dist','heldef','heldefGr'],{
        def g = buildMonAveGr(obj)
        def gN = g.getName().replaceAll(/_aveGr$/,'_heldefGr')
        def gT = g.getTitle().replaceAll(
          /^.*::/,'average defined helicity fraction vs. segment number ::')
        g.setName(gN)
        g.setTitle(gT)
        return g
      })
      T.addLeaf(monTree,[runnum,'helic','dist','heldef','heldefDist'],{
        def h = buildMonAveDist(obj,50,0,1)
        def hN = h.getName().replaceAll(/_aveDist$/,'_heldefDist')
        def hT = h.getTitle().replaceAll(
          /^.*::/,'average defined helicity fraction distribution ::')
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
        monTree[runnum]['helic']['dist']['heldef']['heldefGr'].addPoint(
          segnum, helFrac, segnumDev, 0 )
        monTree[runnum]['helic']['dist']['heldef']['heldefDist'].fill(helFrac)
        monTree[runnum]['helic']['dist']['heldef']['heldefNumer'] += numer
        monTree[runnum]['helic']['dist']['heldef']['heldefDenom'] += denom
      }

      // relative luminosity
      T.addLeaf(monTree,[runnum,'helic','dist','rellum','rellumNumer'],{0})
      T.addLeaf(monTree,[runnum,'helic','dist','rellum','rellumDenom'],{0})
      T.addLeaf(monTree,[runnum,'helic','dist','rellum','rellumGr'],{
        def g = buildMonAveGr(obj)
        def gN = g.getName().replaceAll(/_aveGr$/,'_rellumGr')
        def gT = g.getTitle().replaceAll(
          /^.*::/,'average relative luminosity vs. segment number ::')
        g.setName(gN)
        g.setTitle(gT)
        return g
      })
      T.addLeaf(monTree,[runnum,'helic','dist','rellum','rellumDist'],{
        def h = buildMonAveDist(obj,50,0.9,1.1)
        def hN = h.getName().replaceAll(/_aveDist$/,'_rellumDist')
        def hT = h.getTitle().replaceAll(
          /^.*::/,'average relative luminosity distribution ::')
        h.setName(hN)
        h.setTitle(hT)
        return h
      })
      if(obj.integral()>0) {
        helP = obj.getBinContent(0) // positive helicity is 'helicity==-1' in banks
        helM = obj.getBinContent(2) // negative helicity is 'helicity==+1' in banks
        rellum = helM>0 ? helP / helM : 0
        monTree[runnum]['helic']['dist']['rellum']['rellumGr'].addPoint(
          segnum, rellum, segnumDev, 0 )
        monTree[runnum]['helic']['dist']['rellum']['rellumDist'].fill(rellum)
        monTree[runnum]['helic']['dist']['rellum']['rellumNumer'] += helP
        monTree[runnum]['helic']['dist']['rellum']['rellumDenom'] += helM
      }

    }


    // DIS kinematics monitor
    //---------------------------------
    if(objN.contains("/DIS_")) {
      if(!objN.contains("Q2VsW")) {
        var = tok[1]

        T.addLeaf(monTree,[runnum,'DIS',var,'aveGr'],{buildMonAveGr(obj)})
        T.addLeaf(monTree,[runnum,'DIS',var,'aveDist'],{
          varNB = 100
          if(var=="Q2") { varLB=0; varUB=12; }
          else if(var=="W") { varLB=0; varUB=6; }
          else { varLB=0; varUB=1; }
          buildMonAveDist(obj,varNB,varLB,varUB)
        })

        // add <var> point to the monitors
        if(obj.integral()>0) {
          aveX = obj.getMean()
          aveXerr = 0 // 1.0/Math.sqrt(obj.getIntegral()
          monTree[runnum]['DIS'][var]['aveGr'].addPoint(
            segnum, aveX, segnumDev, aveXerr )
          monTree[runnum]['DIS'][var]['aveDist'].fill(aveX)
        }
      }
    }

    // pion kinematics monitor
    //----------------------------
    if(objN.contains("/inclusive_")) {
      part = tok[1]
      var = tok[2]
      T.addLeaf(monTree,[runnum,'inclusive',part,var,'aveGr'],{buildMonAveGr(obj)})
      T.addLeaf(monTree,[runnum,'inclusive',part,var,'aveDist'],{
        varNB = 100
        varLB = 0
        varUB = 0
        if(var=='p') { varLB=0; varUB=10 }
        else if(var=='pT') { varLB=0; varUB=4 }
        else if(var=='z') { varLB=0; varUB=1 }
        else if(var=='theta') { varLB=0; varUB=Math.toRadians(90.0) }
        else if(var=='phiH') { varLB=-3.15; varUB=3.15 }
        buildMonAveDist(obj,varNB,varLB,varUB)
      })
      if(obj.integral()>0) {
        aveX = obj.getMean()
        aveXerr = 0
        monTree[runnum]['inclusive'][part][var]['aveGr'].addPoint(
          segnum, aveX, segnumDev, aveXerr )
        monTree[runnum]['inclusive'][part][var]['aveDist'].fill(aveX)
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
  if(T.key.contains('Dist')) {
    // initialise new timeline graph, if not yet initialised
    def tlRun = T.leafPath[0]
    def tlPath = T.leafPath[1..-2]
    T.addLeaf(timelineTree,tlPath,{
      def tlN = tlPath.join('_')
      def tlT
      if(tlPath.contains('helic')) {
        if(tlPath.contains('sinPhi')) tlT = "sinPhiH"
        else if(T.key=='heldefDist') tlT = "defined helicity fraction"
        else if(T.key=='rellumDist') tlT = "relative luminosity"
        else tlT = "unknown"
      }
      if(tlPath.contains('DIS')) tlT = "DIS kinematics"
      if(tlPath.contains('inclusive')) {
        if(tlPath.contains('pip')) tlT = "inclusive pi+ kinematics"
        if(tlPath.contains('pim')) tlT = "inclusive pi- kinematics"
      }
      tlT = "average ${tlT} vs. run number"
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
    if(T.key=='heldefDist' ||  T.key=='rellumDist') {
      def ndKey = T.key.replaceAll('Dist','')
      def numer = monTree[tlRun]['helic']['dist'][ndKey]["${ndKey}Numer"]
      def denom = monTree[tlRun]['helic']['dist'][ndKey]["${ndKey}Denom"]
      def frac = denom>0 ? numer/denom : 0
      T.getLeaf(timelineTree,tlPath).addPoint(tlRun,frac,0,0)
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
    // add plots to /$run directory; plots defined for + and - helicities
    // will be renamed such that the front end plots them together
    T.exeLeaves(tree,{
      if(checkFilter(T.leafPath,filterList,T.key)) {
        def name = T.leaf.getName()
        if(name.contains('_hp_')) name = name.replaceAll('_hp_','_')
        else if(name.contains('_hm_')) {
          name = name.replaceAll('_hm_','_')
          name += ":hm"
        }
        T.leaf.setName(name)
        outHipo.addDataSet(T.leaf)
      }
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
hipoWrite("helicity_sinPhi",['helic','sinPhi'])
hipoWrite("helicity_defined_fraction",['helic','dist','heldef'])
hipoWrite("relative_luminosity",['helic','dist','rellum'])
hipoWrite("DIS_kinematics",['DIS'])
hipoWrite("pip_kinematics",['inclusive','pip'])
hipoWrite("pim_kinematics",['inclusive','pim'])

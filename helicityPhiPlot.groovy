import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import java.lang.Math.*
import Tools
Tools T = new Tools()


// get list of input hipo files
def inDir = "outhipo"
def inDirObj = new File(inDir)
def inList = []
def inFilter = ~/sinphi_.*\.hipo/
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
  println name
  def tok = name.tokenize('_')
  name = tok[0..-3].join('_')
  // plot helicity states together
  if(name.contains('_hp_')) name = name.replaceAll('_hp_','_')
  else if(name.contains('_hm_')) {
    name = name.replaceAll('_hm_','_')
    name += ":hm"
  }
  println name
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

// build monitor 'average X vs. segment number'
def buildMonAveGr = { tObj ->
  def grN = objToMonName(tObj.getName())
  def grT = objToMonTitle(tObj.getTitle())
  grN = "averageGr_" + grN
  grT = "average " + grT
  grT.replaceAll('::','vs. segment number ::')
  def gr = new GraphErrors(grN)
  gr.setTitle(grT)
  if(grN.contains(":hm")) { gr.setMarkerColor(2); gr.setLineColor(2); }
  return gr
}

// build monitor 'average X' distribution
def buildMonAveDist = { tObj,nb,lb,ub ->
  def histN = objToMonName(tObj.getName())
  def histT = objToMonTitle(tObj.getTitle())
  histN = "averageDist_" + histN
  histT = "average " + histT
  histT.replaceAll('::','distribution ::')
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

// loop over sinphi hipo files
inList.each { inFile ->
  inTdir.readFile(inFile)
  objList = inTdir.getCompositeObjectList(inTdir)
  objList.each { objN ->
    if(objN.contains("/helic_sinPhi_")) {
      obj = inTdir.getObject(objN)

      // tokenize histogram name to get runnum, segnum, particle type, and helicity
      tok = objN.tokenize('/')[-1].tokenize('_')
      part = tok[2]
      hel = tok[3]
      runnum = tok[4].toInteger()
      segnum = new BigInteger(tok[-2])
      segnumDev = new BigInteger(tok[-1])

      // initialize average monitors
      T.addLeaf(monTree,[runnum,'helic','sinPhi',part,hel,'aveGr'],{
        buildMonAveGr(obj)
      })
      T.addLeaf(monTree,[runnum,'helic','sinPhi',part,hel,'aveDist'],{
        buildMonAveDist(obj,100,-1,1)
      })

      // add <X> point to the monitor
      if(obj.integral()>0) {
        aveX = obj.getMean()
        monTree[runnum]['helic']['sinPhi'][part][hel]['aveGr'].addPoint(
          segnum,
          aveX,
          segnumDev,
          1.0/Math.sqrt(obj.getIntegral())
        )
        monTree[runnum]['helic']['sinPhi'][part][hel]['aveDist'].fill(aveX)

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
  if(T.key=='aveDist') {
    // initialise new timeline graph, if not yet initialised
    def tlPath = T.leafPath[1..-2]
    T.addLeaf(timelineTree,tlPath,{
      def tlN = tlPath.join('_')
      def tl = new GraphErrors(tlN)
    })
    // add this run's <X> to the timeline
    def tlRun = T.leafPath[0]
    aveX = T.leaf.getMean()
    T.getLeaf(timelineTree,tlPath).addPoint(tlRun,aveX,0,0)
  }
})
    

// output everything to a hipo file
def outHipo = new TDirectory()
monTree.each { run,tree ->
  outHipo.mkdir("/${run}")
  outHipo.cd("/${run}")
  T.exeLeaves(tree,{outHipo.addDataSet(T.leaf)})
}
outHipo.mkdir("/timelines")
outHipo.cd("/timelines")
T.exeLeaves(timelineTree,{outHipo.addDataSet(T.leaf)})

def outHipoN = "outhipo/helicityPhi.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)


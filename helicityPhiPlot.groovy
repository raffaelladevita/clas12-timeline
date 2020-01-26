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
  def tok = name.tokenize('_')
  name = tok[0..-2].join('_')
  // plot helicity states together
  if(name.contains('_hp_')) name.replaceAll('_hp_','')
  else if(name.contains('_hm_')) {
    name.replaceAll('_hm_','')
    name += ":hm"
  }
  return name
}

// subroutine to transform an object title into a monitori title
def objToMonTitle = { title ->
  title = title.replaceAll(/ segment=.*$/,'')
  return title
}

// subroutine to build monitor 'average X vs. segment number'
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

      // initialize average <X> monitor, if it hasn't been
      T.addLeaf(monTree[runnum,'helic','sinPhi',part,hel],{buildMonAveGr(obj)})

      // add <X> point to the monitor
      if(obj.integral()>0) {
          monTree[runnum]['helic']['sinPhi'][part][hel].addPoint(
          segnum,
          obj.getMean(),
          segnumDev,
          1.0/Math.sqrt(obj.getIntegral())
        )
      }
    }
  }

  inFile = null // "close" the file
}


//---------------------
// build timelines
//---------------------

// subroutine to fill a timeline
def buildTimeline = { tPart,tHel ->
  def tl = new GraphErrors("${tPart}_${tHel}")
  tl.setTitle("average sinPhiH vs. runnum")
  def g
  def cnt
  def avg
  monTree.each { tRun,bRun ->
    avg = 0
    g = bRun[tPart][tHel]
    cnt = g.getDataSize(0)
    cnt.times { i -> avg += g.getDataY(i) / cnt }
    tl.addPoint(tRun,avg,0,0)
  }
  return tl
}

/*
timelineTree:
particle (pi+,pi-)
 │
 ├ helicity+ : <sinphi> vs. runnum
 └ helicity- : <sinphi> vs. runnum
*/
// build timelineTree by taking the last run's particle & helicity branches and 
// copying that tree structure
def timelineTree = [:]
monTree[runnum].each{ kPart,bPart ->
  timelineTree.put(kPart,[:])
  bPart.each{ kHel,gr ->
    timelineTree[kPart].put(kHel,buildTimeline(kPart,kHel))
  }
}


// output everything to a hipo file
def outHipo = new TDirectory()
monTree.each { kRun,bRun ->
  outHipo.mkdir("/${kRun}")
  outHipo.cd("/${kRun}")
  bRun.each{ kPart,bPart ->
    bPart.each{ kHel,gr ->
      outHipo.addDataSet(gr)
    }
  }
}

outHipo.mkdir("/timelines")
outHipo.cd("/timelines")
timelineTree.each{ kPart,bPart ->
  bPart.each{ kHel,t ->
    outHipo.addDataSet(t)
  }
}

def outHipoN = "outhipo/helicityPhi.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)


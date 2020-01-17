import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import java.lang.Math.*


// get list of sinphi hipo files
def inDir = "outhipo"
def inDirObj = new File(inDir)
def inList = []
def inFilter = ~/sinphi_.*\.hipo/
inDirObj.traverse( type: groovy.io.FileType.FILES, nameFilter: inFilter ) {
  if(it.size()>0) inList << inDir+"/"+it.getName()
}
inList.sort()
inList.each { println it }


// subroutine to build a graph of <sinPhi> vs. xnum, where xnum is a file number 
// or a segment ("time slice") of events
def buildGraph = { tObj ->
  def grN = tObj.getName().tokenize('_').subList(0,3).join('_')
  grN = grN.replaceAll(/_hp$/,"")
  grN = grN.replaceAll(/_hm$/,":hm")
  def grT = tObj.getTitle()
  grT = grT.replaceAll(/ filenum.*$/," vs. file number")
  grT = grT.replaceAll(/ firstEventOfSegment.*$/," vs. segment's 1st eventNum")
  grT = grT.replaceAll(/sinPhi/,"<sinPhi>")
  def gr = new GraphErrors(grN)
  gr.setTitle(grT)
  if(grN.contains(":hm")) { gr.setMarkerColor(2); gr.setLineColor(2); }
  else { gr.setMarkerColor(4); gr.setLineColor(4); }
  return gr
}


//-----------------------------------------
// fill the <sinPhi> vs. xnum graphs
//-----------------------------------------

/*
graphTree:
runnum
│
└ particle (pi+,pi-)
  │
  ├ helicity+ : <sinphi> vs. xnum
  └ helicity- : <sinphi> vs. xnum
*/
def graphTree = [:]

def inTdir = new TDirectory()
def objList
def part,hel
def runnum,xnum
def tok
def obj
def graph

// loop over sinphi hipo files
inList.each { inFile ->
  inTdir.readFile(inFile)
  objList = inTdir.getCompositeObjectList(inTdir)
  objList.each { objN ->
    if(objN.contains("/sinPhi_")) {
      obj = inTdir.getObject(objN)

      // tokenize histogram name to get runnum, xnum, particle type, and helicity
      tok = objN.tokenize('/')[-1].tokenize('_')
      part = tok[1]
      hel = tok[2]
      runnum = tok[3].toInteger()
      xnum = new BigInteger(tok[4])
      xnumDev = tok.size()==6 ? new BigInteger(tok[5]) : 0

      // initialize graph, if it hasn't been
      if(graphTree[runnum]==null) graphTree.put(runnum,[:])
      if(graphTree[runnum][part]==null) graphTree[runnum].put(part,[:])
      if(graphTree[runnum][part][hel]==null) {
        graphTree[runnum][part].put(hel,buildGraph(obj))
      }
      graph = graphTree[runnum][part][hel]

      // add <sinPhi> to the graph
      if(obj.integral()>0) {
        graph.addPoint(
          xnum,
          obj.getMean(),
          xnumDev,
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
  tl.setTitle("<sinPhi> vs. runnum")
  def g
  def cnt
  def avg
  graphTree.each { tRun,bRun ->
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
graphTree[runnum].each{ kPart,bPart ->
  timelineTree.put(kPart,[:])
  bPart.each{ kHel,gr ->
    timelineTree[kPart].put(kHel,buildTimeline(kPart,kHel))
  }
}


// output everything to a hipo file
def outHipo = new TDirectory()
graphTree.each { kRun,bRun ->
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


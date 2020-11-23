import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D
import java.lang.Math.*
import Tools
Tools T = new Tools()


// ARGUMENTS ****************************
def dataset = 'rga_spring19'
if(args.length>=1) dataset = args[0]
// **************************************


// get www dir, by searching datasetList.txt for specified `dataset`, and set
// `indir` and `outdir`
def wwwdir = System.getenv('CLASQAWWW')
if(wwwdir==null) throw new Exception("env vars not set; source env.sh")
def datasetList = new File("datasetList.txt")
def datasetFound = false
def indir,outdir
if(!(datasetList.exists())) throw new Exception("datasetList.txt not found")
datasetList.eachLine { line ->
  def tok = line.tokenize(' ')
  if(dataset==tok[0]) { // find the specified dataset
    indir = wwwdir+"/"+tok[1]
    outdir = wwwdir+"/"+tok[2]
    datasetFound = true
  }
}
if(!datasetFound) throw new Exception("unknown dataset \"$dataset\"")



// define tree "B" of closures for each calibration QA constraint
// -first branch: detectors (=directory names)
// -second branch: hipo file name
// -leaf name: graph name
// -leaf: closure for QA cuts
// -- note: relevant units are in comment
// also define tree "L", which lists bound lines to draw
def B = [:]
def L = [:]

// loop through cuts list
def cutsFile = new File("cuts.txt")
def tok
if(!(cutsFile.exists())) throw new Exception("cuts.txt not found")
def lastWord = { str -> str.tokenize('_')[-1] }
cutsFile.eachLine { line ->
  tok = line.tokenize(' ')
  if(tok.size()==0) return
  cutPath = tok[0..-4]
  def det = cutPath[0]
  def timeline = cutPath[1]
  def spec = cutPath.size()>2 ? cutPath[2] : ''
  def lbound = tok[-3].toDouble()
  def ubound = tok[-2].toDouble()
  def units = tok[-1]

  // add cuts to graph
  def addCut = { graphN ->
    T.addLeaf(B,[det,timeline,graphN],{[lbound,ubound]})
    T.addLeaf(L,[det,timeline],{[]})
    T.getLeaf(L,[det,timeline]).push(lbound)
    T.getLeaf(L,[det,timeline]).push(ubound)
  }

  // graph name convention varies among detector timelines, such as including
  // sector dependence, or layer dependence, or a timeline with a single graph;
  // - here we parse the detector and timeline names and determine which graph(s)
  //   the cut bounds apply to
  if(det=='ft') addCut(timeline.contains('ftc') ? lastWord(timeline) : spec)
  else if(det=='rich') addCut('fwhm_max')
  else if(det=='ctof') addCut(timeline.contains('edep') ? 'Edep' : lastWord(timeline))
  else if(det=='cnd') {
    (1..3).each{ layernum -> addCut('layer'+layernum+' '+lastWord(timeline)) }
  }
  else { // sector dependent detectors
    (1..6).each{ secnum ->
      def sec = 'sec'+secnum
      if(det=='rf') addCut(sec)
      else if(det=='ftof') addCut(sec)
      else if(det=='ltcc' && (secnum==3 || secnum==5)) addCut(sec)
      else if(det=='htcc') addCut(sec)
      else if(det=='ec') {
        if(timeline=='ec_Sampling') addCut(sec)
        else if(secnum==1) addCut(lastWord(timeline)) // sector independent
      }
      else if(det=='dc') {
        (1..6).each{ slnum ->
          def sl = 'sl'+slnum // super layer
          if(spec=='R1' && (slnum==1 || slnum==2)) addCut(sec+' '+sl)
          else if(spec=='R2' && (slnum==3 || slnum==4)) addCut(sec+' '+sl)
          else if(spec=='R3' && (slnum==5 || slnum==6)) addCut(sec+' '+sl)
        }
      }
    }
  }

}
T.exeLeaves(L,{ T.leaf = T.leaf.unique() })


// ==============================================================================

println "=== TIMELINES ========================="
T.exeLeaves(B,{println T.leafPath})
println T.pPrint(L)
println "======================================="


// general closures
def buildLine = { v,color ->
  return new GraphErrors(['plotLine','horizontal',v,color].join(':'))
}


// apply boundaries, for each closure defined above
// - loops through B tree, and read in corresponding input timeline
// - create bad timeline, for runs where a calibration QA constraint fails
// - loop through the input timeline, test constraints, add result to bad
//   timeline as necessary
// - store bad timeline in a tree "TL" with same structure as "T"
def inTdir = new TDirectory()
def gr
def TL = [:]
T.exeLeaves(B,{

  // setup
  def graphPath = T.leafPath
  def fileN = indir+'/'+graphPath[0,-2].join('/') + ".hipo"
  def bounds = T.leaf

  // read input timeline
  def graphN = graphPath[-1]
  T.printStatus("open file=\"$fileN\" graph=\"$graphN\"")
  inTdir.readFile(fileN)
  gr = inTdir.getObject("/timelines/${graphN}")

  // define output bad timeline
  T.addLeaf(TL,graphPath,{
    def g = new GraphErrors()
    g.setName(gr.getName()+"__bad")
    g.setTitle(gr.getTitle())
    gr.setTitleX(gr.getTitleX())
    gr.setTitleY(gr.getTitleY())
    return g
  })


  // loop over runs
  gr.getDataSize(0).times { i ->

    // check QA bounds
    def run = gr.getDataX(i)
    def val = gr.getDataY(i)
    def inbound = val>=bounds[0] && val<=bounds[1]
    if(!inbound) {
      //T.printStatus("OB "+graphPath+" $run $val")
      T.getLeaf(TL,graphPath).addPoint(run,val,0,0)
    }

  }
})


// write output timelines
TL.each{ det, detTr -> // loop through detector directories
  detTr.each{ hipoFile, graphTr -> // loop through timeline hipo files

    // create output TDirectory
    def outTdir = new TDirectory()

    // write graphs
    outTdir.mkdir("/timelines")
    outTdir.cd("/timelines")
    graphTr.each{ graphName, graph -> outTdir.addDataSet(graph) }

    // copy TDirectories for each run from input hipo file
    def inHipoN = "${indir}/${det}/${hipoFile}.hipo"
    inTdir.readFile(inHipoN)
    def inList = inTdir.getCompositeObjectList(inTdir)
    inList.each{ 
      if(!it.contains("timelines")) {
        def rundir = it.tokenize('/')[0]
        outTdir.mkdir("/$rundir")
        outTdir.cd("/$rundir")
        outTdir.addDataSet(inTdir.getObject(it))
      } else {
        outTdir.cd("/timelines")
        def graphName = it.replaceAll(/^.*\//,'')
        def graph = inTdir.getObject(it)
        outTdir.addDataSet(graph)
      }
    }

    // add cut lines
    outTdir.cd("/timelines")
    T.getLeaf(L,[det,hipoFile]).each{ num ->
      outTdir.addDataSet(buildLine(num,'gray'))
    }

    

    // create output hipo file
    def outHipoDir = "${outdir}/${det}"
    "mkdir -p $outHipoDir".execute()
    //"cp -v $inHipoN ${outHipoDir}/".execute()
    def outHipoN = "${outHipoDir}/${hipoFile}_QA.hipo"
    File outHipoFile = new File(outHipoN)
    if(outHipoFile.exists()) outHipoFile.delete()
    outTdir.writeFile(outHipoN)
  }
}

import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D
import java.lang.Math.*
import Tools
Tools T = new Tools()


// ARGUMENTS ****************************
def dataset = 'rga_v2.2.26'
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
// FD ********************************
(1..6).each{ secnum ->
  def s = 'sec'+secnum
  // FTOF
  T.addLeaf(B,['ftof','ftof_edep_p1a_midangles',s],{{ v -> v>9.25 && v<10.5}}) // MeV
  T.addLeaf(B,['ftof','ftof_edep_p1b_midangles',s],{{ v -> v>11.25 && v<12.25}}) // MeV
  T.addLeaf(B,['ftof','ftof_edep_p2',s],{{ v -> v>9.2 && v<10.2}}) // MeV
  T.addLeaf(B,['ftof','ftof_time_p1a_mean',s],{{ v -> Math.abs(v*1e-9) < 15e-12}}) // s
  T.addLeaf(B,['ftof','ftof_time_p1b_mean',s],{{ v -> Math.abs(v*1e-9) < 15e-12}}) // s
  T.addLeaf(B,['ftof','ftof_time_p2_mean',s],{{ v -> Math.abs(v*1e-9) < 50e-12}}) // s
  T.addLeaf(B,['ftof','ftof_time_p1a_sigma',s],{{ v -> v*1e-9 < 125e-12}}) // s
  T.addLeaf(B,['ftof','ftof_time_p1b_sigma',s],{{ v -> v*1e-9 < 70e-12}}) // s
  T.addLeaf(B,['ftof','ftof_time_p2_sigma',s],{{ v -> v*1e-9 < 325e-12}}) // s
}
// CD ********************************
// CTOF -------------
T.addLeaf(B,['ctof','ctof_edep','Edep'],{{ v -> v>5.7 && v<6.3 }}) // MeV
T.addLeaf(L,['ctof','ctof_edep','Edep'],{[5.7,6.3]}) // MeV
//
T.addLeaf(B,['ctof','ctof_time_mean','mean'],{{ v -> Math.abs(v) < 0.020 }}) //ns
T.addLeaf(L,['ctof','ctof_time_mean','mean'],{[-0.020,0.020]}) //ns
//
T.addLeaf(B,['ctof','ctof_time_sigma','sigma'],{{ v -> v < 0.115 }}) //ns
T.addLeaf(L,['ctof','ctof_time_sigma','sigma'],{[0.115]}) //ns
// ***********************************
println "=== TIMELINES ========================="
T.exeLeaves(B,{println T.leafPath})
println "======================================="


// general closures
def buildLine = { g,name,v ->
  new F1D(
    g.getName()+":"+name,
    Double.toString(v),
    g.getDataX(0),
    g.getDataX(g.getDataSize(0)-1)
  )
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
  def graphN = graphPath[-1]
  def checkBounds = T.leaf

  // read input timeline
  println "====== open file=\"$fileN\" graph=\"$graphN\""
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
    def inbound = checkBounds(val)
    if(!inbound) {
      println "OB "+graphPath+" $run $val\n"
      T.getLeaf(TL,graphPath).addPoint(run,val,0,0)
    }

    // set output boolean; apply aesthetic offset for FD sectors
    // deprecated: for boolean timeline
    /*
    def boolval = inbound ? 1:0
    if(gr.getName().contains("sec")) {
      def sector = gr.getName().find(/\d+/).toInteger()
      boolval += sector*0.03
    }
    T.getLeaf(TL,graphPath).addPoint(run,boolval,0,0)
    */
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
    graphTr.each{ graphName, graph -> 
      outTdir.addDataSet(graph)
      /*
      T.getLeaf(L,[det,hipoFile,graphName]).eachWithIndex{ num,idx ->
        outTdir.addDataSet(buildLine(graph,"l$idx",num))
      }
      */
    }

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
        outTdir.addDataSet(inTdir.getObject(it))
      }
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

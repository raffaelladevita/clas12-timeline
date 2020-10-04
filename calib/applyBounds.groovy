import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import java.lang.Math.*
import Tools
Tools T = new Tools()

// get www dir
qadir=System.getenv('CLASQA')
if(qadir==null) {
  System.err << "ERROR: env vars not set; source env.sh\n\n"
  return
}
indir = qadir+"/../www/rga/pass0/v2.2.26"



// define tree "B" of closures for each calibration QA constraint
// -branch paths follow directory path
// -penultimate branch: hipo file name
// -leaf name: graph name
// -leaf: closure
// - relevant units are in comment
def B = [:]
// CTOF
T.addLeaf(B,['ctof','ctof_edep','Edep'],{{ v -> v>5.7 && v<6.3 }}) // MeV
T.addLeaf(B,['ctof','ctof_time_mean','mean'],{{ v -> Math.abs(v*1e-9) < 20e-12 }}) //s
T.addLeaf(B,['ctof','ctof_time_sigma','sigma'],{{ v -> v*1e-9 < 115e-12 }}) //s




// apply boundaries, for each closure defined above
// - loops through B tree, and read in corresponding input timeline
// - create boolean timeline, for whether or not a calibration QA constraint passes
// - loop through the input timeline, test constraints, add result to boolean timeline
// - store boolean timeline in a tree "TL" with same structure as "T"
def inTdir = new TDirectory()
def gr
def TL = [:]
T.exeLeaves(B,{

  // setup
  def graphPath = T.leafPath
  def fileN = indir+'/'+graphPath[0,-2].join('/') + ".hipo"
  def graphN = graphPath[-1]
  def checkBounds = T.leaf
  def inbound

  // read input timeline

  println "====== open file=\"$fileN\" graph=\"$graphN\""
  inTdir.readFile(fileN)
  gr = inTdir.getObject("/timelines/${graphN}")

  // define boolean timeline
  T.addLeaf(TL,graphPath,{
    def g = new GraphErrors()
    g.setName(gr.getName()+"_bound")
    g.setTitle(gr.getTitle())
    gr.setTitleX(gr.getTitleX())
    gr.setTitleY(gr.getTitleY()+" in bounds")
    return g
  })

  // loop over runs, and check boundaries
  gr.getDataSize(0).times { i ->
    def run = gr.getDataX(i)
    def val = gr.getDataY(i)
    inbound = checkBounds(val)
    if(!inbound) println "OB "+graphPath+" $run $val\n"
    T.getLeaf(TL,graphPath).addPoint(run,inbound?1:0,0,0)
  }
})

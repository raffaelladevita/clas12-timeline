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
// FORWARD =====================================================================
(1..6).each{ secnum ->
  def sec = 'sec'+secnum
  // RF ------------------------------------------------------------------------
  T.addLeaf(B,['rf','rftime_electron_FD_mean',sec],{{ v-> Math.abs(v)<0.010 }}) // ns
  T.addLeaf(L,['rf','rftime_electron_FD_mean',sec],{[-0.010,0.010]}) //ns
  //
  T.addLeaf(B,['rf','rftime_electron_FD_sigma',sec],{{ v-> v<0.070 }}) // ns
  T.addLeaf(L,['rf','rftime_electron_FD_sigma',sec],{[0.070]}) //ns
  // FTOF ------------------------------------------------------------------------
  T.addLeaf(B,['ftof','ftof_edep_p1a_midangles',sec],{{ v -> v>9.25 && v<10.5}}) // MeV
  T.addLeaf(L,['ftof','ftof_edep_p1a_midangles',sec],{[9.25,10.5]}) // MeV
  //
  T.addLeaf(B,['ftof','ftof_edep_p1b_midangles',sec],{{ v -> v>11.25 && v<12.25}}) // MeV
  T.addLeaf(L,['ftof','ftof_edep_p1b_midangles',sec],{[11.25,12.25]}) // MeV
  //
  T.addLeaf(B,['ftof','ftof_edep_p2',sec],{{ v -> v>9.2 && v<10.2}}) // MeV
  T.addLeaf(L,['ftof','ftof_edep_p2',sec],{[9.2,10.2]}) // MeV
  //
  T.addLeaf(B,['ftof','ftof_time_p1a_mean',sec],{{ v -> Math.abs(v)<0.025 }}) // ns
  T.addLeaf(L,['ftof','ftof_time_p1a_mean',sec],{[-0.025,0.025]}) // ns
  //
  T.addLeaf(B,['ftof','ftof_time_p1a_sigma',sec],{{ v -> v<0.125 }}) // ns
  T.addLeaf(L,['ftof','ftof_time_p1a_sigma',sec],{[0.125]}) // ns
  //
  T.addLeaf(B,['ftof','ftof_time_p1b_mean',sec],{{ v -> Math.abs(v)<0.015 }}) // ns
  T.addLeaf(L,['ftof','ftof_time_p1b_mean',sec],{[-0.015,0.15]}) // ns
  //
  T.addLeaf(B,['ftof','ftof_time_p1b_sigma',sec],{{ v -> v<0.070 }}) // ns
  T.addLeaf(L,['ftof','ftof_time_p1b_sigma',sec],{[0.070]}) // ns
  //
  T.addLeaf(B,['ftof','ftof_time_p2_mean',sec],{{ v -> Math.abs(v)<0.050 }}) // ns
  T.addLeaf(L,['ftof','ftof_time_p2_mean',sec],{[-0.050,0.050]}) // ns
  //
  T.addLeaf(B,['ftof','ftof_time_p2_sigma',sec],{{ v -> v<0.325 }}) // ns
  T.addLeaf(L,['ftof','ftof_time_p2_sigma',sec],{[0.325]}) // ns
  // LTCC ----------------------------------------------------------------------
  if(secnum==3 || secnum==5) {
    T.addLeaf(B,['ltcc','ltcc_elec_nphe_sec',sec],{{ v -> v>12 && v<14 }})
    T.addLeaf(L,['ltcc','ltcc_elec_nphe_sec',sec],{[12,14]})
  }
  // HTCC ----------------------------------------------------------------------
  T.addLeaf(B,['htcc','htcc_nphe_sec',sec],{{ v -> v>11 && v<13 }})
  T.addLeaf(L,['htcc','htcc_nphe_sec',sec],{[11,13]})
  // ECAL ----------------------------------------------------------------------
  T.addLeaf(B,['ec','ec_Sampling',sec],{{ v -> v>0.24 && v<0.26 }})
  T.addLeaf(L,['ec','ec_Sampling',sec],{[0.24,0.26]})
  //
  if(secnum==1) { // (sector independent)
    T.addLeaf(B,['ec','ec_gg_m_mean','mean'],{{ v -> v>0.131 && v<0.134 }}) // GeV
    T.addLeaf(L,['ec','ec_gg_m_mean','mean'],{[0.131,0.134]}) // GeV
    //
    T.addLeaf(B,['ec','ec_gg_m_sigma','sigma'],{{ v -> v<0.015 }}) // GeV
    T.addLeaf(L,['ec','ec_gg_m_sigma','sigma'],{[0.015]}) // GeV
    //
    T.addLeaf(B,['ec','ec_pim_time_mean','mean'],{{ v -> Math.abs(v)<0.040 }}) // ns
    T.addLeaf(L,['ec','ec_pim_time_mean','mean'],{[-0.040,0.040]}) // ns
    //
    T.addLeaf(B,['ec','ec_pim_time_sigma','sigma'],{{ v -> Math.abs(v)<0.200 }}) // ns
    T.addLeaf(L,['ec','ec_pim_time_sigma','sigma'],{[0.200]}) // ns
    //
    T.addLeaf(B,['ec','ec_pip_time_mean','mean'],{{ v -> Math.abs(v)<0.040 }}) // ns
    T.addLeaf(L,['ec','ec_pip_time_mean','mean'],{[-0.040,0.040]}) // ns
    //
    T.addLeaf(B,['ec','ec_pip_time_sigma','sigma'],{{ v -> Math.abs(v)<0.200 }}) // ns
    T.addLeaf(L,['ec','ec_pip_time_sigma','sigma'],{[0.200]}) // ns
    //
  }
  // DC ----------------------------------------------------------------------
  (1..6).each{ slnum ->
    def sl = 'sl'+slnum // super layer
    T.addLeaf(B,['dc','dc_residuals_sec_sl_mean',sec+' '+sl],{{ v -> v>0.0225 && v<0.0450 }}) // cm
    T.addLeaf(L,['dc','dc_residuals_sec_sl_mean',sec+' '+sl],{[0.0225,0.0450]}) // cm
    //
    T.addLeaf(B,['dc','dc_residuals_sec_sl_sigma',sec+' '+sl],{{ v -> v<0.0250 }}) // cm // CORRECT?
    T.addLeaf(L,['dc','dc_residuals_sec_sl_sigma',sec+' '+sl],{[0.0250]}) // cm // CORRECT?
  }
}
// FT ------------------------------------------------------------------------
T.addLeaf(B,['ft','ftc_pi0_mass_mean','mean'],{{ v -> v>134 && v<136 }}) // MeV
T.addLeaf(L,['ft','ftc_pi0_mass_mean','mean'],{[134,136]}) // MeV
//
T.addLeaf(B,['ft','ftc_pi0_mass_sigma','sigma'],{{ v -> v<5 }}) // MeV
T.addLeaf(L,['ft','ftc_pi0_mass_sigma','sigma'],{[5]}) // MeV
//
T.addLeaf(B,['ft','fth_MIPS_energy','layer1'],{{ v -> v>1.2 && v<1.35 }}) // MeV
T.addLeaf(L,['ft','fth_MIPS_energy','layer1'],{[1.2,1.35]}) // MeV
T.addLeaf(B,['ft','fth_MIPS_energy','layer2'],{{ v -> v>2.7 && v<2.9 }}) // MeV
T.addLeaf(L,['ft','fth_MIPS_energy','layer2'],{[2.7,2.9]}) // MeV
//
T.addLeaf(B,['ft','fth_MIPS_time_mean','layer1'],{{ v -> v>-0.200 && v<0.200 }}) // ns
T.addLeaf(L,['ft','fth_MIPS_time_mean','layer1'],{[-0.200,0.200]}) // ns
T.addLeaf(B,['ft','fth_MIPS_time_mean','layer2'],{{ v -> v>-0.200 && v<0.200 }}) // ns
T.addLeaf(L,['ft','fth_MIPS_time_mean','layer2'],{[-0.200,0.200]}) // ns
//
T.addLeaf(B,['ft','fth_MIPS_time_sigma','layer1'],{{ v -> v<1.35 }}) // ns
T.addLeaf(L,['ft','fth_MIPS_time_sigma','layer1'],{[1.35]}) // ns
T.addLeaf(B,['ft','fth_MIPS_time_sigma','layer2'],{{ v -> v<1.1 }}) // ns
T.addLeaf(L,['ft','fth_MIPS_time_sigma','layer2'],{[1.1]}) // ns
// RICH -----------------------------------------------------------------------
T.addLeaf(B,['rich','rich_time_fwhm_max','fwhm_max'],{{ v -> v<1 }}) // ns
T.addLeaf(L,['rich','rich_time_fwhm_max','fwhm_max'],{[1]}) // ns

// CENTRAL =====================================================================
// CTOF ------------------------------------------------------------------------
T.addLeaf(B,['ctof','ctof_edep','Edep'],{{ v -> v>5.7 && v<6.3 }}) // MeV
T.addLeaf(L,['ctof','ctof_edep','Edep'],{[5.7,6.3]}) // MeV
//
T.addLeaf(B,['ctof','ctof_time_mean','mean'],{{ v -> Math.abs(v)<0.020 }}) // ns
T.addLeaf(L,['ctof','ctof_time_mean','mean'],{[-0.020,0.020]}) // ns
//
T.addLeaf(B,['ctof','ctof_time_sigma','sigma'],{{ v -> v<0.115 }}) // ns
T.addLeaf(L,['ctof','ctof_time_sigma','sigma'],{[0.115]}) // ns
// CND ------------------------------------------------------------------------
(1..3).each{ layernum ->
  def layer = 'layer'+layernum
  T.addLeaf(B,['cnd','cnd_dEdz_mean',layer+' mean'],{{ v -> v>1.75 && v<2.25 }}) // MeV/cm
  T.addLeaf(L,['cnd','cnd_dEdz_mean',layer+' mean'],{[1.75,2.25]}) // MeV/cm
  //
  T.addLeaf(B,['cnd','cnd_dEdz_sigma',layer+' sigma'],{{ v -> v<0.300 }}) // MeV/cm
  T.addLeaf(L,['cnd','cnd_dEdz_sigma',layer+' sigma'],{[0.300]}) // MeV/cm
  //
  T.addLeaf(B,['cnd','cnd_time_neg_vtP_mean',layer+' mean'],{{ v -> Math.abs(v)<0.100 }}) // ns
  T.addLeaf(L,['cnd','cnd_time_neg_vtP_mean',layer+' mean'],{[-0.100,0.100]}) // ns
  //
  T.addLeaf(B,['cnd','cnd_time_neg_vtP_sigma',layer+' sigma'],{{ v -> v<0.300 }}) // ns
  T.addLeaf(L,['cnd','cnd_time_neg_vtP_sigma',layer+' sigma'],{[0.300]}) // ns
  //
  T.addLeaf(B,['cnd','cnd_zdiff_mean',layer+' mean'],{{ v -> Math.abs(v)<0.4 }}) // cm
  T.addLeaf(L,['cnd','cnd_zdiff_mean',layer+' mean'],{[-0.4,0.4]}) // cm
  //
  T.addLeaf(B,['cnd','cnd_zdiff_sigma',layer+' sigma'],{{ v -> v<4 }}) // cm
  T.addLeaf(L,['cnd','cnd_zdiff_sigma',layer+' sigma'],{[4]}) // cm
}


// ==============================================================================
println "=== TIMELINES ========================="
T.exeLeaves(B,{println T.leafPath})
println "======================================="


// general closures
def buildLine = { g,name,color,v ->
  new F1D(
    ['same',g.getName(),name,color].join(':'),
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
  def checkBounds = T.leaf

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
    def inbound = checkBounds(val)
    if(!inbound) {
      T.printStatus("OB "+graphPath+" $run $val")
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
        // add cut lines
        T.getLeaf(L,[det,hipoFile,graphName]).eachWithIndex{ num,idx ->
          outTdir.addDataSet(buildLine(graph,"l$idx",idx==0?'red':'blue',num))
        }
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

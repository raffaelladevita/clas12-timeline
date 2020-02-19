/* create hipo file with plots of N, F, N/F, etc. vs. file number, for each run
 * - this starts to build the structure of the 'timeline' hipo file
 */

import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors

//----------------------------------------------------------------------------------
// ARGUMENTS:
def dataset = 'fall18'
if(args.length>=1) dataset = args[0]
//----------------------------------------------------------------------------------

// define vars and subroutines
def sectors = 0..<6
def sec = { int i -> i+1 }
def tok
int r=0
def runnum, filenum, sector
def ntrig
def fcStart, fcStop
def ufcStart, ufcStop
def fcCharge
def ufcCharge
def trigRat
def errPrint = { str -> System.err << "ERROR in run ${runnum}_${filenum}: "+str+"\n" }

// define graphs
def defineGraph = { name,ytitle ->
  sectors.collect {
    def g = new GraphErrors(name+"_${runnum}_"+sec(it))
    g.setTitle(ytitle+" vs. file number -- run $runnum, sector "+sec(it))
    g.setTitleY(ytitle)
    g.setTitleX("file number")
    return g
  }
}
def grA, grN, grF, grT

// define output hipo file
def outHipo = new TDirectory()
"mkdir -p outhipo.${dataset}".execute()
def outHipoN = "outhipo.${dataset}/plots.hipo"
def writeHipo = { o -> o.each{ outHipo.addDataSet(it) } }
def writePlots = { run ->
  println "write run $run"
  outHipo.mkdir("/${run}")
  outHipo.cd("/${run}")
  writeHipo(grA)
  writeHipo(grN)
  writeHipo(grF)
  writeHipo(grT)
}

// open data_table.dat
def dataFile = new File("outdat.${dataset}/data_table.dat")
def runnumTmp = 0
if(!(dataFile.exists())) throw new Exception("data_table.dat not found")
dataFile.eachLine { line ->

  // read columns of data_table.dat (in order left-to-right)
  tok = line.tokenize(' ')
  r=0
  runnum = tok[r++].toInteger()
  filenum = tok[r++].toInteger()
  sector = tok[r++].toInteger()
  ntrig = tok[r++].toFloat()
  fcStart = tok[r++].toFloat()
  fcStop = tok[r++].toFloat()
  ufcStart = tok[r++].toFloat()
  ufcStop = tok[r++].toFloat()


  // if the run number changed, write filled graphs, then start new graphs
  if(runnum!=runnumTmp) {
    if(runnumTmp>0) writePlots(runnumTmp)
    grA = defineGraph("grA","Electron Trigger N/F")
    grN = defineGraph("grN","Number electron trigs N")
    grF = defineGraph("grF","Faraday cup charge F [nC]")
    grT = defineGraph("grT","Live Time")
    runnumTmp = runnum
  }


  // calculations
  fcCharge = fcStop - fcStart
  ufcCharge = ufcStop - ufcStart
  if(fcCharge<=0) errPrint("fcCharge = ${fcCharge} <= 0")
  if(ufcCharge<=0) errPrint("ufcCharge = ${ufcCharge} <= 0")
  trigRat = fcCharge!=0 ? ntrig/fcCharge : 0
  liveTime = ufcCharge!=0 ? fcCharge/ufcCharge : 0

  // add points to graphs
  s = sector-1
  if(s<0||s>5) { errPrint("bad sector number $sector") }
  else {
    grA[s].addPoint(filenum,trigRat,0,0)
    grN[s].addPoint(filenum,ntrig,0,0)
    grF[s].addPoint(filenum,fcCharge,0,0)
    grT[s].addPoint(filenum,liveTime,0,0)
  }

} // eo loop through data_table.dat
writePlots(runnum) // write last run's graphs


// write hipo file
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)

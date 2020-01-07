import org.jlab.io.hipo.HipoDataSource
import org.jlab.clas.physics.Particle
import org.jlab.clas.physics.Vector3
import org.jlab.groot.data.H1F
import org.jlab.groot.data.TDirectory
import groovy.json.JsonOutput
import java.lang.Math.*

// OPTIONS
def inHipoType = "skim" // options: "dst", "skim"

// ARGUMENTS
def inHipo = "skim/skim4_5052.hipo" // directory of DST files, or a single SKIM file
if(args.length>=1) inHipo = args[0]
println "inHipo=$inHipo"


// check options
if( inHipoType!="dst" && inHipoType!="skim") {
  System.err << "ERROR: bad inHipoType\n"
  return
}
println "inHipoType=$inHipoType"


// get list of input HIPO files
def inHipoList = []
if(inHipoType=="dst") {
  def inHipoDirObj = new File(inHipo)
  def inHipoFilter = inHipoFilter = ~/dst_.*\.hipo/
  inHipoDirObj.traverse( type: groovy.io.FileType.FILES, nameFilter: inHipoFilter ) {
    if(it.size()>0) inHipoList << inHipo+"/"+it.getName()
  }
  inHipoList.sort()
}
else if(inHipoType=="skim") { inHipoList << inHipo }


// define sinPhi histograms
def partT = [ 'pip':'pi+', 'pim':'pi-' ]
def helT = [ 'hp':'hel+', 'hm':'hel-' ]
def buildHist = { partN,helN,runn,xn ->
  new H1F(
    "sinPhi_${partN}_${helN}_${runn}_${xn}",
    "sinPhi "+partT[partN]+" "+helT[helN]+" run${runn} file${xn}",
    100,-1,1
  )
}
/*
histTree:
particle
 ├ helicity+ : <sinphi> vs. xnum
 └ helicity- : <sinphi> vs. xnum
*/
def histTree = [:]
partT.each{ histTree[it.key] = [:] }


// define variables
def event
def pidList = []
def particleBank
def configBank
def eventBank
def pipList = []
def pimList = []
def eventNum
def helicity
def helStr
def helDefined
def phi
def xnum
def xnumTmp = -1
def runnum
def runnumTmp = -1
def reader
def inHipoName
def evCount

// subroutine which returns a list of Particle objects of a certain PID, satisfying 
// desired cuts
def findParticles = { pid ->
  def partList
  // get list of bank rows corresponding to this PID
  def rowList = pidList.findIndexValues{ it == pid }.collect{it as Integer}
  //println "pid=$pid  found in rows $rowList"
  // apply cuts
  //rowList = rowList.findAll{ particleBank.getShort('status',it)<0 }
  // get list of Particle objects
  partList = rowList.collect { row ->
    new Particle(pid,*['px','py','pz'].collect{particleBank.getFloat(it,row)})
  }
  return partList
}


// define output file
def outHipo = new TDirectory()


// loop through files
inHipoList.each { inHipoFile ->

  // get runnum and xnum, where xnum is what will be plotted on the horizontal axis
  // - for DST files, xnum = filenum; we define the corresponding histos here
  // - for SKIM files, xnum = event number (or a similar proxy); we define
  //   the histograms later in the event loop, as needed
  inHipoName = inHipoFile.tokenize('/')[-1]
  if(inHipoType=="dst") {
    runnum = inHipoName.tokenize('.')[0].tokenize('_')[-1].toInteger()
    xnum = inHipoName.tokenize('.')[-2].tokenize('-')[0].toInteger()
    println "runnum=$runnum  xnum=$xnum"
    histTree.each{ part,h ->
      h['hp'] = buildHist(part,'hp',runnum,xnum)
      h['hm'] = buildHist(part,'hm',runnum,xnum)
    }
  }
  else if(inHipoType=="skim") {
    runnum = inHipoName.tokenize('.')[0].tokenize('_')[-1].toInteger()
  }

  // define outHipo directory
  if(runnum!=runnumTmp) {
    if(runnumTmp>0) System.err << "WARNING: run number changed\n"
    outHipo.mkdir("/$runnum")
    outHipo.cd("/$runnum")
    runnumTmp = runnum
  }


  // open hipo reader
  reader = new HipoDataSource()
  reader.open(inHipoFile)


  // event loop
  evCount = 0
  while(reader.hasEvent()) {

    evCount++
    //if(evCount>100000) break // limiter
    if(evCount % 100000 == 0) println "analyzed $evCount events"

    // if reading skim file, get xnum, and either define new histograms or
    // write out the filled ones
    if(inHipoType=="skim") {
      xnum = (evCount/10000).toInteger()
      if(xnum!=xnumTmp) {
        // write out filled histogram
        if(xnumTmp>=0) {
          histTree.each{ pName,pMap -> 
            pMap.each{ hName,histo -> 
              outHipo.addDataSet(histo) 
              histo = null
            } 
          }
        }
        // define new histograms
        histTree.each{ part,h ->
          h['hp'] = buildHist(part,'hp',runnum,xnum)
          h['hm'] = buildHist(part,'hm',runnum,xnum)
        }
        // update tmp number
        xnumTmp = xnum
      }
    }

    // read event
    event = reader.getNextEvent()
    if(event.hasBank("REC::Particle") &&
       event.hasBank("REC::Event") &&
       event.hasBank("RUN::config") ){

      particleBank = event.getBank("REC::Particle")
      eventBank = event.getBank("REC::Event")
      configBank = event.getBank("RUN::config")

      // get run number, event number, and helicity 
      eventNum = configBank.getInt('event',0)
      helicity = eventBank.getByte('helicity',0)
      helDefined = true
      switch(helicity) {
        case 1:
          helStr = 'hm'
          break
        case -1:
          helStr = 'hp'
          break
        default:
          helDefined = false
          //helStr = 'hp' // override
          break
      }

      // proceed if helicity is defined
      if(helDefined) {

        // get list of PIDs, with list index corresponding to bank row
        pidList = (0..<particleBank.rows()).collect{ particleBank.getInt('pid',it) }
        //println "pidList = $pidList"

        // get lists of pions
        pipList = findParticles(211)
        pimList = findParticles(-211)

        pipList.each{ histTree['pip'][helStr].fill(Math.sin(it.phi())) }
        pimList.each{ histTree['pim'][helStr].fill(Math.sin(it.phi())) }
      }
    }

  } // end event loop
  reader.close()

  // write histograms to hipo file, and then set them to null for garbage collection
  histTree.each{ pName,pMap -> 
    pMap.each{ hName,histo -> 
      outHipo.addDataSet(histo) 
      histo = null
    } 
  }
}


outHipoN = "outhipo/sinphi_${runnum}.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)

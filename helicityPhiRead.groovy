import org.jlab.io.hipo.HipoDataSource
import org.jlab.clas.physics.Particle
import org.jlab.clas.physics.Vector3
import org.jlab.groot.data.H1F
import org.jlab.groot.data.TDirectory
import org.jlab.clas.physics.LorentzVector
import org.jlab.clas.physics.Vector3
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
def buildHist = { partN, helN, runn, xn ->
  new H1F(
    "sinPhi_${partN}_${helN}_${runn}_${xn}",
    "sinPhi "+partT[partN]+" "+helT[helN]+" run=${runn} filenum=${xn}",
    100,-1,1
  )
}
/*
histTree:
particle
 ├ helicity+ : <sinphi> distribution for segment (or filenum)
 └ helicity- : <sinphi> distribution for segment (or filenum)
*/
def histTree = [:]
partT.each{ histTree[it.key] = [:] }
def histN,histT


// define variables
def event
def pidList = []
def particleBank
def configBank
def eventBank
def pipList = []
def pimList = []
def eleList = []
def eventNum
def eventNumList = []
def eventNumAve
def eventNumDev
def helicity
def helStr
def helDefined
def phi
def runnum
def runnumTmp = -1
def reader
def inHipoName
def evCount
def segment
def segmentTmp = -1

// lorentz vectors
def vecBeam = new LorentzVector(0, 0, 10.6, 10.6)
def vecTarget = new LorentzVector(0, 0, 0, 0.938)
def vecQ,vecH
def z

// scalar product of 4-vectors
def lorentzDot = { v1,v2 -> return v1.e()*v2.e() - v1.vect().dot(v2.vect()) }

// subroutine which returns a list of Particle objects of a certain PID, satisfying 
// desired cuts
def findParticles = { pid ->

  // get list of bank rows corresponding to this PID
  def rowList = pidList.findIndexValues{ it == pid }.collect{it as Integer}
  //println "pid=$pid  found in rows $rowList"

  // get list of Particle objects
  def partList = rowList.collect { row ->
    new Particle(pid,*['px','py','pz'].collect{particleBank.getFloat(it,row)})
  }

  // apply cuts
  // - electrons
  if(pid==11) {
    partList = partList.max{ it.e() } // choose max-E electron
    partList = partList.findAll{ it.e()>2 && it.e()<11 } // cut E>2
    if(partList.size()>0) {
      vecQ = new LorentzVector(vecBeam)
      vecQ.sub(partList[0].vector()) // virtual photon momentum
    }
  }
  // - pions
  if(Math.abs(pid)==211) {
    // z cut
    partList = partList.findAll{ pion ->
      vecH = new LorentzVector(pion.vector())
      z = lorentzDot(vecTarget,vecH) / lorentzDot(vecTarget,vecQ)
      z>0.3 && z<1
    }
  }

  return partList
}


// define output file
def outHipo = new TDirectory()


// loop through files
inHipoList.each { inHipoFile ->

  // get runnum and filenum
  // - for DST files, we define the corresponding histos here
  inHipoName = inHipoFile.tokenize('/')[-1]
  if(inHipoType=="dst") {
    runnum = inHipoName.tokenize('.')[0].tokenize('_')[-1].toInteger()
    filenum = inHipoName.tokenize('.')[-2].tokenize('-')[0].toInteger()
    println "runnum=$runnum  filenum=$filenum"
    histTree.each{ part,h ->
      h['hp'] = buildHist(part,'hp',runnum,filenum)
      h['hm'] = buildHist(part,'hm',runnum,filenum)
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

  // subroutine to write out to hipo file (if reading skim file)
  def writeHistos = {
    if(inHipoType=="skim") {
      // get average event number; then clear list of events
      eventNumAve = Math.round( eventNumList.sum() / eventNumList.size() )
      eventNumDev = Math.round(Math.sqrt( 
       eventNumList.collect{ n -> Math.pow((n-eventNumAve),2) }.sum() / 
       (eventNumList.size()-1)
      ))
      println "eventNumAve=$eventNumAve  eventNumDev=$eventNumDev"
      eventNumList.clear()
      // loop through histTree, adding histos to the hipo file;
      // note that the average event number is appended to the name and title
      histTree.each{ pName,pMap -> 
        pMap.each{ hName,histo -> 
          histN = histo.getName().replaceAll(/0$/,"${eventNumAve}_${eventNumDev}")
          histT = histo.getTitle().replaceAll(
            / filenum.*$/," vs. segment's <eventNum>")
          histo.setName(histN)
          histo.setTitle(histT)
          outHipo.addDataSet(histo) 
          histo = null
        } 
      }
    }
  }


  //----------------------
  // event loop
  //----------------------
  evCount = 0
  while(reader.hasEvent()) {
    //if(evCount>100000) break // limiter
    event = reader.getNextEvent()

    if(event.hasBank("REC::Particle") &&
       event.hasBank("REC::Event") &&
       event.hasBank("RUN::config") ){

      // get banks
      particleBank = event.getBank("REC::Particle")
      eventBank = event.getBank("REC::Event")
      configBank = event.getBank("RUN::config")

      // if reading skim file, get segment number, and either define new histograms or
      // write out the filled ones
      if(inHipoType=="skim") {

        // segment iterator
        segment = (evCount/10000).toInteger()

        // if segment changed:
        if(segment!=segmentTmp) {

          // if this isn't the first segment, write out filled histograms
          if(segmentTmp>=0) writeHistos()

          // define new histograms (with filenum argument "0", to be replaced later
          // by average event number for this segment)
          histTree.each{ part,h ->
            h['hp'] = buildHist(part,'hp',runnum,0)
            h['hm'] = buildHist(part,'hm',runnum,0)
          }

          // update tmp number
          segmentTmp = segment
        }
      }

      // get helicity
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

        // find scattered electron, and store it in eleList (eleList.size()  should = 1)
        eleList = findParticles(11)
        if(eleList.size()==1) {

          // get lists of pions
          pipList = findParticles(211)
          pimList = findParticles(-211)

          // fill sinPhi distributions
          pipList.each{ histTree['pip'][helStr].fill(Math.sin(it.phi())) }
          pimList.each{ histTree['pim'][helStr].fill(Math.sin(it.phi())) }

          // increment evCount and add event number to event number list
          if(pipList.size()>0 || pimList.size()>0) {
            evCount++
            if(evCount % 100000 == 0) println "found $evCount events which contain a pion"
            eventNum = BigInteger.valueOf(configBank.getInt('event',0))
            if(inHipoType=="skim") eventNumList.add(eventNum)
          }
        }
      }
    }

  } // end event loop
  reader.close()

  // write histograms to hipo file, and then set them to null for garbage collection
  if(inHipoType=="skim") writeHistos()
  else if(inHipoType=="dst") {
    histTree.each{ pName,pMap -> 
      pMap.each{ hName,histo -> 
        outHipo.addDataSet(histo) 
        histo = null
      } 
    }
  }
  reader = null
  System.gc()
}


outHipoN = "outhipo/sinphi_${runnum}.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)

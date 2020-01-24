// fill distributions of sin(phiH) for pions, etc.
// -note: search for 'CUT' to find which cuts are applied

import org.jlab.io.hipo.HipoDataSource
import org.jlab.clas.physics.Particle
import org.jlab.clas.physics.Vector3
import org.jlab.groot.data.H1F
import org.jlab.groot.data.H2F
import org.jlab.groot.data.TDirectory
import org.jlab.clas.physics.LorentzVector
import org.jlab.clas.physics.Vector3
import groovy.json.JsonOutput
import java.lang.Math.*
import groovy.json.JsonOutput

// OPTIONS
def segmentSize = 10000 // number of events in each segment


// ARGUMENTS
def inHipo = "skim/skim4_5052.hipo" // directory of DST files, or a single SKIM file
if(args.length>=1) inHipo = args[0]
println "inHipo=$inHipo"


// property lists
def propT = [ 
  'pip':'pi+',
  'pim':'pi-', 
  'hp':'hel+',
  'hm':'hel-',
  'hu':'hel?'
]
def partList = [ 'pip', 'pim' ]
def helList = [ 'hp', 'hm' ]
def heluList = [ 'hp', 'hm', 'hu' ]


// subroutine to build a histogram
def buildHist = { histName, propList, runn, nb, lb, ub ->
  new H1F(
    "${histName}_" + propList.join('_') + "_${runn}",
    "${histName} " + propList.collect{propT[it]}.join(' ') + " run=$runn",
    nb,lb,ub
  )
}
// subroutine to append segment number to a histogram name & title
def appendSegnum = { hist, segn ->
  def hN = hist.getName() + "_$segn"
  def hT = hist.getTitle() + " seg=$segn"
  hist.setName(hN)
  hist.setTitle(hT)
}


/*
histTree:
|
- sinPhi : <sinPhi> distribution
| - particle : pi+/pi-
|   - helicity : +/-
|
- helic: helicity distribution
| - helicity : +/-/?
|
- kinDist: kinematic distributions
  - Q2dist
  - Wdist
  - zdist
  | - particle : pi+/pi-
  - phiHdist
    - particle : pi+/pi-
*/
def histTree = [:]


// build a tree recursively, with branch levels specified by levelList
// - call the subroutine 'buildTree' to initiate
def rec_buildTree 
rec_buildTree = { levelList, tree, lev ->
  def levNew = lev
  levelList.each{ branches ->
    if(lev==levNew) {
      branches.each{ branch ->
        if(levelList.size()==1) tree[branch] = 0
        else {
          tree[branch] = [:]
          levNew = rec_buildTree(levelList[1..-1],tree[branch],lev+1)
        }
      }
    }
  }
  return lev
}
def buildTree = { treeName, levelList -> 
  rec_buildTree( [[treeName],*levelList], histTree, 0 )
}

// build trees
buildTree('sinPhi',[partList,helList])
buildTree('helic',[heluList])
println new groovy.json.JsonBuilder( histTree ).toPrettyString()

// subroutine to get a leaf
def getLeaf = { tree,path ->
  def node = tree
  path.each { node = node[it] }
  return node
}

// execute a closure on all leaves which stem from a given node
def exeLeaves
def leaf
def leafPath
def leafKey
exeLeaves = { node,path,clos ->
  if(node.getClass()==java.util.LinkedHashMap) {
    node.each { 
      path.push(it.key)
      exeLeaves(it.value,path,clos)
      if(it.value.getClass()!=java.util.LinkedHashMap) {
        it.value = leaf
      }
      path.pop()
    }
  }
  else {
    leaf = node
    leafPath = path
    clos()
  }
}
exeLeaves( histTree, [], { println "$leafPath $leaf"} )
println "---"
exeLeaves( histTree.sinPhi, [], { leaf++; println leaf } )
println "---"
exeLeaves( histTree, [], { println "$leafPath $leaf"} )
println "---"
println getLeaf(histTree.sinPhi,['pip','hp'])
println getLeaf(histTree,['sinPhi','pip','hp'])
return 




// define "overall" histograms (q2, z, phiH, etc.)
// - the ones pertaining to pions are maps to histograms, e.g., zdist['pip'] for pi+ z
def nbins = 50
def q2dist = new H1F("q2dist","Q2 distribution",nbins,0,12)
def Wdist = new H1F("Wdist","W distribution",nbins,0,6)
def q2vsW = new H2F("q2vsW","Q2 vs. W",nbins,0,6,nbins,0,12)
def zdist = partList.collectEntries{ [ (it) :
  new H1F("zdist_${it}",propT[it]+" z distribution",nbins,0,1) ] }
def phiHdist = partList.collectEntries{ [ (it) :
  new H1F("phiHdist_${it}",propT[it]+" phiH distribution",nbins,-3.15,3.15) ] }



// define variables
def event
def pidList = []
def particleBank
def configBank
def eventBank
def pipList = []
def pimList = []
def electron
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
def evCount
def segment
def segmentTmp = -1

// lorentz vectors
def vecBeam = new LorentzVector(0, 0, 10.6, 10.6)
def vecTarget = new LorentzVector(0, 0, 0, 0.938)
def vecEle = new LorentzVector()
def vecH = new LorentzVector()
def vecQ = new LorentzVector()
def vecW = new LorentzVector()

// subroutine to calculate scalar product of 4-vectors
def lorentzDot = { v1,v2 -> return v1.e()*v2.e() - v1.vect().dot(v2.vect()) }

// subroutine to calculate angle between two planes (used for PhiH)
def crossAB,crossCD
def sgn
def numer
def denom
def planeAngle = { vA,vB,vC,vD ->
  crossAB = vA.cross(vB) // AxB
  crossCD = vC.cross(vD) // CxD

  // calculate sign of (AxB).D
  sgn = crossAB.dot(vD) // (AxB).D
  if(Math.abs(sgn)<0.00001) return -10000
  sgn /= Math.abs(sgn)

  // calculate numerator and denominator 
  numer = crossAB.dot(crossCD) // (AxB).(CxD)
  denom = crossAB.mag() * crossCD.mag() // |AxB|*|CxD|
  if(Math.abs(denom)<0.00001) return -10000

  // return angle
  return sgn * Math.acos(numer/denom)
}
  

// subroutine which returns a list of Particle objects of a certain PID
def findParticles = { pid ->

  // get list of bank rows corresponding to this PID
  def rowList = pidList.findIndexValues{ it == pid }.collect{it as Integer}
  //println "pid=$pid  found in rows $rowList"

  // return list of Particle objects
  return rowList.collect { row ->
    new Particle(pid,*['px','py','pz'].collect{particleBank.getFloat(it,row)})
  }
}


// subroutine to calculate hadron (pion) kinematics, and fill histograms
// note: needs to have some kinematics defined (vecQ,q2,W), and helStr
def q2
def W
def z
def phiH
def countEvent
def fillHistos = { list, partN ->
  list.each { part ->

    // calculate z
    vecH.copy(part.vector())
    z = lorentzDot(vecTarget,vecH) / lorentzDot(vecTarget,vecQ)

    // CUT: particle z
    if(z>0.3 && z<1) {

      // calculate phiH
      phiH = planeAngle( vecQ.vect(), vecEle.vect(), vecQ.vect(), vecH.vect() )
      if(phiH>-10000) {

        // fill histograms
        histTree['sinPhi'][partN][helStr].fill(Math.sin(phiH))
        q2dist.fill(q2)
        Wdist.fill(W)
        q2vsW.fill(W,q2)
        zdist[partN].fill(z)
        phiHdist[partN].fill(phiH)

        // tell event counter that this event has at least one particle added to histos
        countEvent = true
      }
    }
  }
}


// define output file
def outHipo = new TDirectory()


// get runnum
runnum = inHipo.tokenize('.')[-2].tokenize('_')[-1].toInteger()


// define outHipo directory
if(runnum!=runnumTmp) {
  if(runnumTmp>0) System.err << "WARNING: run number changed\n"
  outHipo.mkdir("/$runnum")
  outHipo.cd("/$runnum")
  runnumTmp = runnum
}


// open hipo reader
reader = new HipoDataSource()
reader.open(inHipo)

// subroutine to write out to hipo file
def histN,histT
def writeHistos = {
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
  histTree['sinPhi'].each{ pName,pMap -> 
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


    // get segment number
    segment = (evCount/segmentSize).toInteger()

    // if segment number changed, write out filled histos 
    // and/or create new histos
    if(segment!=segmentTmp) {

      // if this isn't the first segment, write out filled histograms
      if(segmentTmp>=0) writeHistos()

      // define new histograms (with filenum argument "0", to be replaced later
      // by average event number for this segment)
      exeLeaves( histTree.sinPhi, [], 
        { hist,path -> hist = buildHist('sinPhi',path,runnum,100,-1,1) } )
      

      // update tmp number
      segmentTmp = segment
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

      // CUT: find scattered electron: highest-E electron such that 2 < E < 11
      electron = findParticles(11).findAll{ it.e()>2 && it.e()<11 }.max{it.e()}
      if(electron) {

        // calculate Q2
        vecQ.copy(vecBeam)
        vecEle.copy(electron.vector())
        vecQ.sub(vecEle) 
        q2 = -1*vecQ.mass2()

        // calculate W
        vecW.copy(vecBeam)
        vecW.add(vecTarget)
        vecW.sub(vecEle)
        W = vecW.mass()

        // CUT: q2 and W
        if( q2>1 && W>2 ) {

          // get lists of pions
          pipList = findParticles(211)
          pimList = findParticles(-211)

          // calculate pion kinematics and fill histograms
          countEvent = false
          fillHistos(pipList,'pip')
          fillHistos(pimList,'pim')

          // increment evCount and add event number to event number list
          if(countEvent) {
            evCount++
            if(evCount % 100000 == 0) println "found $evCount events which contain a pion"
            eventNum = BigInteger.valueOf(configBank.getInt('event',0))
            eventNumList.add(eventNum)
          }

        }
      }
    }
  }

} // end event loop
reader.close()

// write histograms to hipo file, and then set them to null for garbage collection
writeHistos()


// close reader
reader = null
System.gc()


// add overall histos to outHipo
outHipo.mkdir("/histos")
outHipo.cd("/histos")
outHipo.addDataSet(q2dist)
outHipo.addDataSet(Wdist)
outHipo.addDataSet(q2vsW)
zdist.each{outHipo.addDataSet(it.value)}
phiHdist.each{outHipo.addDataSet(it.value)}


// write outHipo file
outHipoN = "outhipo/sinphi_${runnum}.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)

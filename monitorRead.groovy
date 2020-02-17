// a more general monitor, for things like <sinPhiH> or helicity
// - this reads DST files or skim files
// - can be run on slurm
// - note: search for 'CUT' to find which cuts are applied

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
import Tools // (make sure `.` is in $CLASSPATH)
Tools T = new Tools()

// OPTIONS
def segmentSize = 10000 // number of events in each segment
def EBEAM = 10.6 // beam energy (shouldn't be hard-coded... TODO)


// ARGUMENTS
def inHipo = "skim/skim4_5052.hipo" // directory of DST files, or a single SKIM file
if(args.length>=1) inHipo = args[0]
println "inHipo=$inHipo"

// get runnum
def runnum
if(inHipo.contains('postprocess'))
  runnum = inHipo.tokenize('.')[-2].tokenize('/')[-1].toInteger()
else
  runnum = inHipo.tokenize('.')[-2].tokenize('_')[-1].toInteger()
println "runnum=$runnum"


// property lists
def partList = [ 'pip', 'pim' ]
def helList = [ 'hp', 'hm' ]
def heluList = [ 'hp', 'hm', 'hu' ]


// build tree 'histTree', for storing histograms
def histTree = [:]

T.buildTree(histTree,'helic',[
  ['sinPhi'],
  partList,
  helList
],{ new H1F() })

T.buildTree(histTree,'helic',[
  ['dist']
],{ new H1F() })

T.buildTree(histTree,'DIS',[
  ['Q2','W','x','y']
],{ new H1F() })

T.buildTree(histTree,"DIS",[
  ['Q2VsW']
],{ new H2F() })

T.buildTree(histTree,"inclusive",[
  partList,
  ['p','pT','z','theta','phiH']
],{ new H1F() })

println("---\nhistTree:"); 
T.printTree(histTree,{T.leaf.getClass()});
println("---")


// subroutine to build a histogram
def buildHist(histName, histTitle, propList, runn, nb, lb, ub, nb2=0, lb2=0, ub2=0) {

  def propT = [ 
    'pip':'pi+',
    'pim':'pi-', 
    'hp':'hel+',
    'hm':'hel-',
    'hu':'hel?'
  ]

  def pn = propList.join('_')
  def pt = propList.collect{ propT.containsKey(it) ? propT[it] : it }.join(' ')
  if(propList.size()>0) { pn+='_'; pt+=' ' }

  def sn = propList.size()>0 ? '_':''
  def st = propList.size()>0 ? ' ':''
  def hn = "${histName}_${pn}${runn}"
  def ht = "${histTitle} ${pt}:: run=${runn}"

  if(nb2==0) return new H1F(hn,ht,nb,lb,ub)
  else return new H2F(hn,ht,nb,lb,ub,nb2,lb2,ub2)
}


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
def runnumTmp = -1
def reader
def evCount
def segment
def segmentTmp = -1
def nbins


// lorentz vectors
def vecBeam = new LorentzVector(0, 0, EBEAM, EBEAM)
def vecTarget = new LorentzVector(0, 0, 0, 0.938)
def vecEle = new LorentzVector()
def vecH = new LorentzVector()
def vecQ = new LorentzVector()
def vecW = new LorentzVector()


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
// note: needs to have some kinematics defined (vecQ,Q2,W), and helStr
def Q2
def W
def nu
def x,y,z
def p,pT,theta,phiH
def countEvent
def fillHistos = { list, partN ->
  list.each { part ->

    // calculate z
    vecH.copy(part.vector())
    z = T.lorentzDot(vecTarget,vecH) / T.lorentzDot(vecTarget,vecQ)

    // CUT: particle z
    if(z>0.3 && z<1) {

      // calculate momenta, theta, phiH
      p = vecH.p()
      pT = Math.hypot( vecH.px(), vecH.py() )
      theta = vecH.theta()
      phiH = T.planeAngle( vecQ.vect(), vecEle.vect(), vecQ.vect(), vecH.vect() )

      // CUT: if phiH is defined
      if(phiH>-10000) {

        // fill histograms
        histTree['helic']['sinPhi'][partN][helStr].fill(Math.sin(phiH))
        histTree['inclusive'][partN]['p'].fill(p)
        histTree['inclusive'][partN]['pT'].fill(pT)
        histTree['inclusive'][partN]['z'].fill(z)
        histTree['inclusive'][partN]['theta'].fill(theta)
        histTree['inclusive'][partN]['phiH'].fill(phiH)

        // tell event counter that this event has at least one particle added to histos
        countEvent = true
      }
    }
  }
}


// subroutine to write out to hipo file
def outHipo = new TDirectory()
outHipo.mkdir("/$runnum")
outHipo.cd("/$runnum")
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
  // note that the average event number is appended to the name
  T.exeLeaves( histTree, {
    histN = T.leaf.getName() + "_${eventNumAve}_${eventNumDev}"
    histT = T.leaf.getTitle() + " segment=${eventNumAve}"
    T.leaf.setName(histN)
    T.leaf.setTitle(histT)
    outHipo.addDataSet(T.leaf) 
  })
  //println "write histograms:"; T.printTree(histTree,{T.leaf.getName()})
}


//----------------------
// event loop
//----------------------
evCount = 0
reader = new HipoDataSource()
reader.open(inHipo)
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

      // define new histograms
      nbins = 50
      T.exeLeaves( histTree.helic.sinPhi, {
        T.leaf = buildHist('helic_sinPhi','sinPhiH',T.leafPath,runnum,nbins,-1,1) 
      })
      histTree.helic.dist = buildHist('helic_dist','helicity distribution',[],runnum,3,-1,2)
      histTree.DIS.Q2 = buildHist('DIS_Q2','Q^2 distribution',[],runnum,nbins,0,12)
      histTree.DIS.W = buildHist('DIS_W','W distribution',[],runnum,nbins,0,6)
      histTree.DIS.x = buildHist('DIS_x','x distribution',[],runnum,nbins,0,1)
      histTree.DIS.y = buildHist('DIS_y','y distribution',[],runnum,nbins,0,1)
      histTree.DIS.Q2VsW = buildHist('DIS_Q2VsW','Q^2 vs W',[],runnum,nbins,0,6,nbins,0,12)

      T.exeLeaves( histTree.inclusive, {
        def lbound=0
        def ubound=0
        if(T.key=='p') { lbound=0; ubound=10 }
        else if(T.key=='pT') { lbound=0; ubound=4 }
        else if(T.key=='z') { lbound=0; ubound=1 }
        else if(T.key=='theta') { lbound=0; ubound=Math.toRadians(90.0) }
        else if(T.key=='phiH') { lbound=-3.15; ubound=3.15 }
        T.leaf = buildHist('inclusive','',T.leafPath,runnum,nbins,lbound,ubound)
      })

      // print the histogram names and titles
      if(segmentTmp==-1) {
        println "---\nhistogram names and titles:"
        T.printTree(histTree,{ T.leaf.getName() +" ::: "+ T.leaf.getTitle() })
        println "---"
      }

      // update tmp number
      segmentTmp = segment
    }

    // get helicity and fill helicity distribution
    helicity = eventBank.getByte('helicity',0)
    histTree.helic.dist.fill(helicity)
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
        Q2 = -1*vecQ.mass2()

        // calculate W
        vecW.copy(vecBeam)
        vecW.add(vecTarget)
        vecW.sub(vecEle)
        W = vecW.mass()

        // calculate x and y
        nu = vecBeam.e() - vecEle.e()
        x = Q2 / ( 2 * 0.938272 * nu )
        y = nu / EBEAM


        // CUT: Q2 and W and y
        if( Q2>1 && W>2 && y<0.8) {

          // get lists of pions
          pipList = findParticles(211)
          pimList = findParticles(-211)

          // calculate pion kinematics and fill histograms
          // countEvent will be set to true if a pion is added to the histos 
          countEvent = false
          if(helDefined) {
            fillHistos(pipList,'pip')
            fillHistos(pimList,'pim')
          }

          if(countEvent) {

            // fill event-level histograms
            histTree.DIS.Q2.fill(Q2)
            histTree.DIS.W.fill(W)
            histTree.DIS.x.fill(x)
            histTree.DIS.y.fill(y)
            histTree.DIS.Q2VsW.fill(W,Q2)

            // increment event counter
            evCount++
            if(evCount % 100000 == 0) println "found $evCount events which contain a pion"

            // add eventNum to the list of this segment's event numbers
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

// write outHipo file
outHipoN = "outmon/monitor_${runnum}.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)

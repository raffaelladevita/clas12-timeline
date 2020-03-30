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
import org.jlab.detector.base.DetectorType
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.lang.Math.*
import Tools // (make sure `.` is in $CLASSPATH)
Tools T = new Tools()

// OPTIONS
def segmentSize = 10000 // number of events in each segment
def EBEAM = 10.6 // beam energy (shouldn't be hard-coded... TODO)
def inHipoType = "skim" // options: "dst", "skim"


// ARGUMENTS
def inHipo = "skim/skim4_5052.hipo" // directory of DST files, or a single SKIM file
if(args.length>=1) inHipo = args[0]
if(args.length>=2) inHipoType = args[1]


// get hipo file names
def inHipoList = []
if(inHipoType=="dst") {
  def inHipoDirObj = new File(inHipo)
  def inHipoFilter = inHipoFilter = ~/.*\.hipo/
  inHipoDirObj.traverse( type: groovy.io.FileType.FILES, nameFilter: inHipoFilter ) {
    if(it.size()>0) inHipoList << inHipo+"/"+it.getName()
  }
  inHipoList.sort()
  if(inHipoList.size()==0) {
    System.err << "ERROR: no hipo files found in this directory\n"
    return
  }
}
else if(inHipoType=="skim") { inHipoList << inHipo }
else {
  System.err << "ERROR: unknown inHipoType setting\n"
  return
}


// get runnum
def runnum
if(inHipoType=="skim") {
  if(inHipo.contains('postprocess'))
    runnum = inHipo.tokenize('.')[-2].tokenize('/')[-1].toInteger()
  else
    runnum = inHipo.tokenize('.')[-2].tokenize('_')[-1].toInteger()
}
else if(inHipoType=="dst") {
  runnum = inHipo.tokenize('/')[-1].toInteger()
}
println "runnum=$runnum"


// if reading a DST file, load Faraday cup json file, and prepare output table
def fcFileName
def slurp
def fcFile
def fcMapRun
if(inHipoType=="dst") {
  fcFileName = "fcdata.json"
  slurp = new JsonSlurper()
  fcFile = new File(fcFileName)
  fcMapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
}
"mkdir -p outdat".execute()
def datfile = new File("outdat/data_table_${runnum}.dat")
def datfileWriter = datfile.newWriter(false)



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
  def ht = "${pt} ${histTitle}:: run=${runn}"

  if(nb2==0) return new H1F(hn,ht,nb,lb,ub)
  else return new H2F(hn,ht,nb,lb,ub,nb2,lb2,ub2)
}


// define variables
def event
def pidList = []
def particleBank
def configBank
def eventBank
def calBank
def pipList = []
def pimList = []
def eleList = []
def disElectron
def eventNum
def eventNumList = []
def segmentNum
def segmentDev
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
def sectors = 0..<6
def nElec = sectors.collect{0}
def nElecFT = 0
def detIdEC = DetectorType.ECAL.getDetectorId()

// lorentz vectors
def vecBeam = new LorentzVector(0, 0, EBEAM, EBEAM)
def vecTarget = new LorentzVector(0, 0, 0, 0.938)
def vecEle = new LorentzVector()
def vecH = new LorentzVector()
def vecQ = new LorentzVector()
def vecW = new LorentzVector()


// subroutine to increment the number of trigger electrons
def countTriggerElectrons = { eleRows ->

  // cut on chi2pid
  // - for trigger electrons (status<0), chi2pid is defined
  // - for forward tagger electrons (status might not be <0, chi2pid==0.0
  def eleIndList = eleRows.findAll{
    Math.abs(particleBank.getFloat('chi2pid',it)) < 3
  }

  // loop over electrons
  if(eleIndList.size()>0) {
    eleIndList.each { ind ->

      def status = particleBank.getShort('status',ind)

      // trigger electrons
      if(status<0) {
        def eleSec = (0..calBank.rows()).collect{
          ( calBank.getShort('pindex',it).toInteger() == ind &&
            calBank.getByte('detector',it).toInteger() == detIdEC ) ?
            calBank.getByte('sector',it).toInteger() : null
        }.find()
        if(eleSec!=null) nElec[eleSec-1]++
        else {
          System.err <<
            "WARNING: found electron with unknown sector" <<
            " run=${runnum}\n"
        }
      }

      // forward tagger electrons
      if( Math.abs(status/1000).toInteger() & 1 ) {
        nElecFT++
      }
    }
  }
}


// subroutine which returns a list of Particle objects of a certain PID
def findParticles = { pid ->

  // get list of bank rows corresponding to this PID
  def rowList = pidList.findIndexValues{ it == pid }.collect{it as Integer}
  //println "pid=$pid  found in rows $rowList"

  // if looking for electrons, also count the number of trigger electrons
  if(pid==11) countTriggerElectrons(rowList)

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
        if(helDefined) {
          histTree['helic']['sinPhi'][partN][helStr].fill(Math.sin(phiH))
        }
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
  // get segment number
  if(inHipoType=="skim") {
    // segment number is average event number; include standard deviation
    // of event number as well
    segmentNum = Math.round( eventNumList.sum() / eventNumList.size() )
    segmentDev = Math.round(Math.sqrt( 
     eventNumList.collect{ n -> Math.pow((n-segmentNum),2) }.sum() / 
     (eventNumList.size()-1)
    ))
    println "eventNumAve=$segmentNum  eventNumDev=$segmentDev"
    eventNumList.clear()
  }
  else if(inHipoType=="dst") {
    // segment number is the DST 5-file number; standard devation is irrelevant here
    // and set to 0 for compatibility with downstream code
    segmentNum = segmentTmp
    segmentDev = 0
  }

  // loop through histTree, adding histos to the hipo file;
  // note that the average event number is appended to the name
  T.exeLeaves( histTree, {
    histN = T.leaf.getName() + "_${segmentNum}_${segmentDev}"
    histT = T.leaf.getTitle() + " segment=${segmentNum}"
    T.leaf.setName(histN)
    T.leaf.setTitle(histT)
    outHipo.addDataSet(T.leaf) 
  })
  //println "write histograms:"; T.printTree(histTree,{T.leaf.getName()})

  // write number of electrons / FC, if reading dst file
  if(inHipoType=="dst") {
    // get faraday cup data
    def fcMapRunFiles
    def fcVals
    def ufcVals
    def fcStart, fcStop
    def ufcStart, ufcStop
    if(fcMapRun) fcMapRunFiles = fcMapRun.groupBy{ it.fnum }.get(segmentNum)
    if(fcMapRunFiles) {
      // "gated" and "ungated" were switched in hipo files...
      fcVals=fcMapRunFiles.find()."data"."fcup" // actually gated
      ufcVals=fcMapRunFiles.find()."data"."fcupgated" // actually ungated
    }
    if(fcVals && ufcVals) {
      fcStart = fcVals."min"
      fcStop = fcVals."max"
      ufcStart = ufcVals."min"
      ufcStop = ufcVals."max"
    }
    else {
      System.err << 
        "WARNING: faraday cup values not found for" <<
        " run=${runnum} file=${segmentNum}\n"
      fcStart = 0
      fcStop = 0
      ufcStart = 0
      ufcStop = 0
    }
    if(fcStart>fcStop || ufcStart>ufcStop) {
      System.err <<
        "WARNING: faraday cup start > stop for" <<
        " run=${runnum} file=${segmentNum}\n"
    }

    // write to datfile
    sectors.each{ sec ->
      datfileWriter << [ runnum, segmentNum, sec+1 ].join(' ') << ' '
      datfileWriter << [ nElec[sec], nElecFT ].join(' ') << ' '
      datfileWriter << [ fcStart, fcStop, ufcStart, ufcStop ].join(' ') << '\n'
    }

    // reset number of trigger electrons counter
    nElec = sectors.collect{0}
    nElecFT = 0
  }
}



//----------------------
// event loop
//----------------------
evCount = 0
inHipoList.each { inHipoFile ->
  reader = new HipoDataSource()
  reader.open(inHipoFile)
  while(reader.hasEvent()) {
    //if(evCount>100000) break // limiter
    event = reader.getNextEvent()

    if(event.hasBank("REC::Particle") &&
       event.hasBank("REC::Event") &&
       event.hasBank("RUN::config") &&
       event.hasBank("REC::Calorimeter") ){

      // get banks
      particleBank = event.getBank("REC::Particle")
      eventBank = event.getBank("REC::Event")
      configBank = event.getBank("RUN::config")
      calBank = event.getBank("REC::Calorimeter")

      // get list of PIDs, with list index corresponding to bank row
      pidList = (0..<particleBank.rows()).collect{ particleBank.getInt('pid',it) }
      //println "pidList = $pidList"


      // get segment number
      if(inHipoType=="skim") {
        segment = (evCount/segmentSize).toInteger()
      }
      else if(inHipoType=="dst") {
        segment = inHipoFile.tokenize('.')[-2].tokenize('-')[0].toInteger()
      }

      // if segment number changed, write out filled histos 
      // and/or create new histos
      if(segment!=segmentTmp) {

        // if this isn't the first segment, and if we are reading a skim file,
        // write out filled histograms; note that if reading a dst file, this
        // subroutine is instead called at the end of the event loop
        if(segmentTmp>=0 && inHipoType=="skim") writeHistos()

        // define new histograms
        nbins = 50
        T.exeLeaves( histTree.helic.sinPhi, {
          T.leaf = buildHist('helic_sinPhi','sinPhiH',T.leafPath,runnum,nbins,-1,1) 
        })
        histTree.helic.dist = buildHist('helic_dist','helicity',[],runnum,3,-1,2)
        histTree.DIS.Q2 = buildHist('DIS_Q2','Q^2',[],runnum,nbins,0,12)
        histTree.DIS.W = buildHist('DIS_W','W',[],runnum,nbins,0,6)
        histTree.DIS.x = buildHist('DIS_x','x',[],runnum,nbins,0,1)
        histTree.DIS.y = buildHist('DIS_y','y',[],runnum,nbins,0,1)
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
        /*
        if(segmentTmp==-1) {
          println "---\nhistogram names and titles:"
          T.printTree(histTree,{ T.leaf.getName() +" ::: "+ T.leaf.getTitle() })
          println "---"
        }
        */

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


      // get electron list, and increment the number of trigger electrons
      eleList = findParticles(11)


      // CUT: proceed if helicity is defined, unless we are reading a DST file, wherein
      //      helicity is not defined
      if(helDefined || inHipoType=="dst") {


        // CUT: find scattered electron: highest-E electron such that 2 < E < 11
        disElectron = eleList.findAll{ it.e()>2 && it.e()<11 }.max{it.e()}
        if(disElectron) {

          // calculate Q2
          vecQ.copy(vecBeam)
          vecEle.copy(disElectron.vector())
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
            fillHistos(pipList,'pip')
            fillHistos(pimList,'pim')

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
              if(inHipoType=="skim") {
                eventNum = BigInteger.valueOf(configBank.getInt('event',0))
                eventNumList.add(eventNum)
              }
            }
          }
        }
      }
    }

  } // end event loop
  reader.close()

  // write histograms to hipo file, and then set them to null for garbage collection
  segmentTmp = segment
  writeHistos()

  // close reader
  reader = null
  System.gc()
} // end loop over hipo files

// write outHipo file
outHipoN = "outmon/monitor_${runnum}.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)
if(inHipoType=="dst") datfileWriter.close()

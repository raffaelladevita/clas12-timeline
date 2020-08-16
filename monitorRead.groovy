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
def inHipoType = "dst" // options: "dst", "skim"


// ARGUMENTS
def inHipo = "skim/skim4_5052.hipo" // directory of DST files, or a single SKIM file
if(args.length>=1) inHipo = args[0]
if(args.length>=2) inHipoType = args[1]


// get hipo file names
def inHipoList = []
if(inHipoType=="dst") {
  def inHipoDirObj = new File(inHipo)
  def inHipoFilter = ~/.*\.hipo/
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


// prepare output table for electron count and FC charge
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

T.buildTree(histTree,'helic',[
  ['distGoodOnly']
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

/*
println("---\nhistTree:"); 
T.printTree(histTree,{T.leaf.getClass()});
println("---")
*/


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
  if(propList.size()>0) { pn+='_'; }

  def sn = propList.size()>0 ? '_':''
  def st = propList.size()>0 ? ' ':''
  def hn = "${histName}_${pn}${runn}"
  def ht = "${pt} ${histTitle}"

  if(nb2==0) return new H1F(hn,ht,nb,lb,ub)
  else return new H2F(hn,ht,nb,lb,ub,nb2,lb2,ub2)
}


// define variables
def event
def pidList = []
def particleBank
def FTparticleBank
def configBank
def eventBank
def calBank
def scalerBank
def pipList = []
def pimList = []
def eleList = []
def disElectron
def disEleFound
def eleSec
def eventNum
def eventNumList = []
def eventNumMin, eventNumMax
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
def fcup, fcupgated
def LTlist = []
def UFClist = []
def UFClistSorted = []
def UFClistHel = ['hp':[],'hm':[]]
def rellumG, rellumU
def detIdEC = DetectorType.ECAL.getDetectorId()
def Q2
def W
def nu
def x,y,z
def p,pT,theta,phiH
def countEvent
def caseCountNtrigGT1 = 0
def caseCountNFTwithTrig = 0
def nElecTotal

// lorentz vectors
def vecBeam = new LorentzVector(0, 0, EBEAM, EBEAM)
def vecTarget = new LorentzVector(0, 0, 0, 0.938)
def vecEle = new LorentzVector()
def vecH = new LorentzVector()
def vecQ = new LorentzVector()
def vecW = new LorentzVector()


// subroutine to increment the number of counted electrons
def disElectronInTrigger
def disElectronInFT
def countTriggerElectrons = { eleRows,eleParts ->

  // reset some vars
  def Emax = 0
  def Etmp
  disElectronInTrigger = false
  disElectronInFT = false
  def nTrigger = 0
  def nFT = 0
  disEleFound = false

  // loop over electrons from REC::Particle
  if(eleRows.size()>0) {
    eleRows.eachWithIndex { row,ind ->

      def status = particleBank.getShort('status',row)
      def chi2pid = particleBank.getFloat('chi2pid',row)

      // TRIGGER ELECTRONS (FD or CD) CUT
      // - must have status<0 and FD or CD bit(s) set
      // - must have |chi2pid|<3
      // - must appear in ECAL, to obtain sector
      if( status<0 &&
          ( Math.abs(status/1000).toInteger() & 0x2 || 
            Math.abs(status/1000).toInteger() & 0x4 ) &&
          Math.abs(chi2pid)<3
      ) {

        // get sector
        def eleSecTmp = (0..calBank.rows()).collect{
          ( calBank.getShort('pindex',it).toInteger() == row &&
            calBank.getByte('detector',it).toInteger() == detIdEC ) ?
            calBank.getByte('sector',it).toInteger() : null
        }.find()

        // CUT for electron: sector must be defined
        if(eleSecTmp!=null) {

          nTrigger++ // count how many trigger electrons we looked at

          // CUT for electron: choose maximum energy electron (for triggers)
          // - choice is from both trigger and FT electron sets (see below)
          Etmp = eleParts[ind].e()
          if(Etmp>Emax) {
            Emax = Etmp
            eleSec = eleSecTmp
            disElectronInTrigger = true
            disElectronInFT = false
            disElectron = eleParts[ind]
          }

        } else {
          System.err << "WARNING: found electron with unknown sector\n"
        }
      }


      // FT trigger electrons
      // - REC::Particle:status has FT bit
      // - must also appear in RECFT::Particle with status<0 and FT bit
      // - must have E > 300 MeV
      if( Math.abs(status/1000).toInteger() & 0x1 ) {
        if( FTparticleBank.rows() > row ) {
          def FTpid = FTparticleBank.getInt('pid',row)
          def FTstatus = FTparticleBank.getShort('status',row)
          if( FTpid==11 && 
              FTstatus<0 && 
              Math.abs(FTstatus/1000).toInteger() & 0x1 &&
              eleParts[ind].e() > 0.3
          ) {

            nFT++ // count how many FT electrons we looked at

            // CUT for electron: maximum energy electron (for FT)
            // - choice is from both trigger and FT electron sets (see above)
            Etmp = eleParts[ind].e()
            if(Etmp>Emax) {
              Emax = Etmp
              disElectronInFT = true
              disElectronInTrigger = false
              disElectron = eleParts[ind]
            }

          }
        }
      }


    } // eo loop through REC::Particle
  } // eo if nonempty REC::Particle


  // calculate DIS kinematics and increment counters
  if(disElectronInTrigger || disElectronInFT) {

    
    // - calculate DIS kinematics
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


    // CUT for electron: Q2 cut
    //if(Q2<2.5) return


    // - increment counters, and set `disEleFound`
    if(disElectronInTrigger && disElectronInFT) { // can never happen (failsafe)
      System.err << "ERROR: disElectronInTrigger && disElectronInFT == 1; skip event\n"
      return
    }
    else if(disElectronInTrigger) {
      nElec[eleSec-1]++
      disEleFound = true
    }
    else if(disElectronInFT) {
      nElecFT++
      disEleFound = true
    }

    // increment 'case counters' (for studying overlap of trigger/FT cuts)
    // - case where there are more than one trigger electron in FD
    if(disElectronInTrigger && nTrigger>1)
      caseCountNtrigGT1 += nTrigger-1 // count number of unanalyzed extra electrons
    // - case where disElectron is in FT, but there are trigger electrons in FD
    if(disElectronInFT && nTrigger>0)
      caseCountNFTwithTrig += nTrigger // count number of unanalyzed trigger (FD) electrons

  }

}


// subroutine which returns a list of Particle objects of a certain PID
def findParticles = { pid ->

  // get list of bank rows and Particle objects corresponding to this PID
  def rowList = pidList.findIndexValues{ it == pid }.collect{it as Integer}
  def particleList = rowList.collect { row ->
    new Particle(pid,*['px','py','pz'].collect{particleBank.getFloat(it,row)})
  }
  //println "pid=$pid  found in rows $rowList"

  // if looking for electrons, also count the number of trigger electrons,
  // and find the DIS electron
  if(pid==11) countTriggerElectrons(rowList,particleList)

  // return list of Particle objects
  return particleList
}



// subroutine to calculate hadron (pion) kinematics, and fill histograms
// note: needs to have some kinematics defined (vecQ,Q2,W), and helStr
def fillHistos = { list, partN ->
  list.each { part ->

    // calculate z
    vecH.copy(part.vector())
    z = T.lorentzDot(vecTarget,vecH) / T.lorentzDot(vecTarget,vecQ)

    // CUT for pions: particle z
    if(z>0.3 && z<1) {

      // calculate momenta, theta, phiH
      p = vecH.p()
      pT = Math.hypot( vecH.px(), vecH.py() )
      theta = vecH.theta()
      phiH = T.planeAngle( vecQ.vect(), vecEle.vect(), vecQ.vect(), vecH.vect() )

      // CUT for pions: if phiH is defined
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

  // get event number range
  eventNumMin = eventNumList.min()
  eventNumMax = eventNumList.max()

  // proceed only if there are data to write
  if(eventNumList.size()>0 && UFClist.size()>0) {

    // get segment number
    if(inHipoType=="skim") {
      // segment number is average event number; include standard deviation
      // of event number as well
      segmentNum = Math.round( eventNumList.sum() / eventNumList.size() )
      segmentDev = Math.round(Math.sqrt( 
       eventNumList.collect{ n -> Math.pow((n-segmentNum),2) }.sum() / 
       (eventNumList.size()-1)
      ))
      print "eventNum ave=$segmentNum dev=$segmentDev"
      print "min=$eventNumMin max=$eventNumMax\n"
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
      histT = T.leaf.getTitle() + " :: segment=${segmentNum}"
      T.leaf.setName(histN)
      T.leaf.setTitle(histT)
      outHipo.addDataSet(T.leaf) 
    })
    //println "write histograms:"; T.printTree(histTree,{T.leaf.getName()})


    // get FC charge
    LTlist.removeAll{it<0} // remove undefined livetime values
    def aveLivetime = LTlist.size()>0 ? LTlist.sum() / LTlist.size() : 0
    def ufcStart = UFClist.min()
    def ufcStop = UFClist.max()
    def fcStart = ufcStart * aveLivetime
    def fcStop = ufcStop * aveLivetime
    if(fcStart>fcStop || ufcStart>ufcStop) {
      System.err << "WARNING: faraday cup start > stop for" <<
        " run=${runnum} file=${segmentNum}\n"
    }

    // FC charge from each helicity state
    // - disabled for now (not so useful...)
    /*
    println "computing relative luminosity from FC charge..."
    UFClistSorted = UFClist.sort()
    def ufcPrev = 0
    def ufcP = 0
    def ufcM = 0
    def fcP = 0
    def fcM = 0
    UFClistSorted.eachWithIndex{ ufc,i ->
      if(i==0) ufcPrev = ufc // unfortunately, 1st event is forced to be ignored
      else {
        def fcount = 0
        if(ufc in UFClistHel.hp) {
          ufcP += ufc-ufcPrev
          fcP += (ufc-ufcPrev) * aveLivetime
          fcount++
        }
        if(ufc in UFClistHel.hm) {
          ufcM += ufc-ufcPrev
          fcM += (ufc-ufcPrev) * aveLivetime
          fcount++
        } 
        if(fcount>1) System.err << "WARNING: double-count in relative luminosity\n"
        if(ufc<=ufcPrev) System.err << "WARNING: UFClistSorted is not sorted or has a duplicate\n"
        ufcPrev = ufc
      }
    }
    rellumU = ufcP>0 ? ufcP/ufcM : 0 // ungated
    rellumG = fcP>0 ? fcP/fcM : 0 // gated
    println "--> relative luminosity:"
    println "    gated = $rellumG"
    println "  ungated = $rellumU"
    println " difference = "+(rellumU-rellumG)
    */


    // write number of electrons and FC charge to datfile
    sectors.each{ sec ->
      datfileWriter << [ runnum, segmentNum ].join(' ') << ' '
      datfileWriter << [ eventNumMin, eventNumMax ].join(' ') << ' '
      datfileWriter << [ sec+1, nElec[sec], nElecFT ].join(' ') << ' '
      datfileWriter << [ fcStart, fcStop, ufcStart, ufcStop ].join(' ') << '\n'
      //datfileWriter << [ fcP, fcM, ufcP, ufcM ].join(' ') << '\n'
    }

    // print some stats
    /*
    nElecTotal = nElec*.value.sum()
    println "\nnumber of trigger electrons: $nElecTotal" 
    println """number of electrons that satisified FD trigger cuts, but were not analyzed...
    ...because they had subdominant E: $caseCountNtrigGT1
    ...because there was a higher-E electron satisfying FT cuts: $caseCountNFTwithTrig""" 
    caseCountNtrigGT1=0
    caseCountNFTwithTrig=0
    */
  }
  else {
    System.err << "WARNING: empty segment (segmentTmp=$segmentTmp)\n"
    System.err << " if all segments in a run are empty, there will be more errors later!"
  }

  // reset number of trigger electrons counter and FC lists
  nElec = sectors.collect{0}
  nElecFT = 0
  UFClist = []
  UFClistSorted = []
  UFClistHel.hp = []
  UFClistHel.hm = []
  LTlist = []
  eventNumList.clear()
}



//----------------------
// event loop
//----------------------
evCount = 0
inHipoList.each { inHipoFile ->

  // open skim/DST file
  reader = new HipoDataSource()
  reader.open(inHipoFile)

  // if DST file, set segment number to 5-file number
  if(inHipoType=="dst")
    segment = inHipoFile.tokenize('.')[-2].tokenize('-')[0].toInteger()

  // EVENT LOOP
  while(reader.hasEvent()) {
    //if(evCount>100000) break // limiter
    event = reader.getNextEvent()

    if(event.hasBank("REC::Particle") &&
       event.hasBank("REC::Event") &&
       event.hasBank("RUN::config") ) {

      // get required banks
      particleBank = event.getBank("REC::Particle")
      eventBank = event.getBank("REC::Event")
      configBank = event.getBank("RUN::config")
      // get additional banks
      FTparticleBank = event.getBank("RECFT::Particle")
      calBank = event.getBank("REC::Calorimeter")
      scalerBank = event.getBank("RUN::scaler")


      // get list of PIDs, with list index corresponding to bank row
      pidList = (0..<particleBank.rows()).collect{ particleBank.getInt('pid',it) }
      //println "pidList = $pidList"


      // update segment number, if reading skim file
      if(inHipoType=="skim") segment = (evCount/segmentSize).toInteger()

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
        histTree.helic.distGoodOnly = buildHist('helic_distGoodOnly','helicity (with electron cuts)',[],runnum,3,-1,2)
        histTree.DIS.Q2 = buildHist('DIS_Q2','Q^2',[],runnum,2*nbins,0,12)
        histTree.DIS.W = buildHist('DIS_W','W',[],runnum,2*nbins,0,6)
        histTree.DIS.x = buildHist('DIS_x','x',[],runnum,2*nbins,0,1)
        histTree.DIS.y = buildHist('DIS_y','y',[],runnum,2*nbins,0,1)
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

      
      // get FC charge
      if(scalerBank.rows()>0) {
        fcup = scalerBank.getFloat("fcup",0) // ungated charge
        //fcupgated = scalerBank.getFloat("fcupgated",0) // do not use!
        UFClist << fcup
        //FClist << fcupgated
        /*
        if(helDefined) {
          UFClistHel[helStr] << fcup
          //FClistHel[helStr] << fcupgated
        }
        */
        LTlist << scalerBank.getFloat("livetime",0) // livetime
      }


      // get electron list, and increment the number of trigger electrons
      // - also finds the DIS electron, and calculates x,Q2,W,y,nu
      eleList = findParticles(11) // (`eleList` is unused)


      // CUT: if a dis electron was found (see countTriggerElectrons)
      if(disEleFound) {

        if(disElectronInTrigger)
          histTree.helic.distGoodOnly.fill(helicity)

        // CUT for pions: Q2 and W and y and helicity
        if( Q2>1 && W>2 && y<0.8 && helDefined) {

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

          }
        }
      }

      // add eventNum to the list of this segment's event numbers
      eventNum = BigInteger.valueOf(configBank.getInt('event',0))
      eventNumList.add(eventNum)

    } // end if event has specific banks

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


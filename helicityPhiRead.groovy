import org.jlab.io.hipo.HipoDataSource
import org.jlab.clas.physics.Particle
import org.jlab.clas.physics.Vector3
import org.jlab.groot.data.H1F
import org.jlab.groot.data.TDirectory
import groovy.json.JsonOutput
import java.lang.Math.*


// ARGUMENTS
def dstDir = "dst"
if(args.length>=1) dstDir = args[0]
println "dstDir=$dstDir"


// get list of DST files
def dstDirObj = new File(dstDir)
def dstList = []
def dstFilter = ~/dst_.*\.hipo/
dstDirObj.traverse( type: groovy.io.FileType.FILES, nameFilter: dstFilter ) {
  if(it.size()>0) dstList << dstDir+"/"+it.getName()
}
dstList.sort()


// define sinPhi histograms
def partT = [ 'pip':'pi+', 'pim':'pi-' ]
def helT = [ 'hp':'hel+', 'hm':'hel-' ]
def buildHist = { partN,helN,runn,filen ->
  new H1F(
    "sinPhi_${partN}_${helN}_${runn}_${filen}",
    "sinPhi "+partT[partN]+" "+helT[helN]+" run${runn} file${filen}",
    100,-1,1
  )
}
def hist = [:]
partT.each{ hist[it.key] = [:] }


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
def filenum
def runnum
def reader
def dstName
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
def outHipoN = dstDir.tokenize('/')[-1]
outHipo.mkdir("/"+outHipoN)
outHipo.cd("/"+outHipoN)


// loop through files
dstList.each { dstFile ->

  // get filenum
  dstName = dstFile.tokenize('/')[-1]
  filenum = dstName.tokenize('.')[-2].tokenize('-')[0].toInteger()
  runnum = dstName.tokenize('.')[0].tokenize('_')[-1].toInteger()
  println "runnum=$runnum  filenum=$filenum"

  // define new histograms
  hist.each{ part,h ->
    h['hp'] = buildHist(part,'hp',runnum,filenum)
    h['hm'] = buildHist(part,'hm',runnum,filenum)
  }

  // open hipo reader
  reader = new HipoDataSource()
  reader.open(dstFile)


  // event loop
  evCount = 0
  while(reader.hasEvent()) {

    evCount++
    //if(evCount>1000) break // limiter
    if(evCount % 100000 == 0) println "analyzed $evCount events"

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
          //helDefined = false
          helStr = 'hp' // override
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


        pipList.each{ hist['pip'][helStr].fill(Math.sin(it.phi())) }
        pimList.each{ hist['pim'][helStr].fill(Math.sin(it.phi())) }
      }


    }
  }
  reader.close()

  // write histograms to hipo file
  // and then set them to null for garbage collection
  hist.each{ pName,pMap -> 
    pMap.each{ hName,histo -> 
      outHipo.addDataSet(histo) 
      histo = null
    } 
  }
  reader = null
  System.gc()
}


outHipoN = "outhipo/sinphi_"+outHipoN+".hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)



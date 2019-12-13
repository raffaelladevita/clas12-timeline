import org.jlab.io.hipo.HipoDataSource
import org.jlab.clas.physics.Particle
import org.jlab.clas.physics.Vector3


def dstName = args[0]

def reader = new HipoDataSource()
reader.open(dstName)

// define variables
def pidList = []
def particleBank
def event
def bankRows
def pipList = []
def pimList = []

// subroutine which returns a list of Particle objects of a certain PID, satisfying 
// desired cuts
def findParticles = { pid ->
  def partList
  // get list of bank rows corresponding to this PID
  def rowList = pidList.findIndexValues{ it == pid }.collect{it as Integer}
  println "pid=$pid  found in rows $rowList"
  // apply cuts
  //rowList = rowList.findAll{ particleBank.getShort('status',it)<0 }
  // get list of Particle objects
  partList = rowList.collect { row ->
    new Particle(pid,*['px','py','pz'].collect{particleBank.getFloat(it,row)})
  }
  return partList
}


// event loop
while(reader.hasEvent()) {
  event = reader.getNextEvent()
  if(event.hasBank("REC::Particle")) {

    println "---"
    particleBank = event.getBank("REC::Particle")
    bankRows = 0..<particleBank.rows()

    // get list of PIDs, with list index corresponding to bank row
    pidList = bankRows.collect{ particleBank.getInt('pid',it) }
    println "pidList = $pidList"

    // get lists of pions
    pipList = findParticles(211)
    pimList = findParticles(-211)

    pipList.each{
      println it.phi()
    }


  }
}

reader.close()

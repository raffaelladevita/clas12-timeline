import org.jlab.groot.data.TDirectory
import static groovy.io.FileType.FILES
import org.jlab.groot.math.F1D
import org.jlab.groot.data.GraphErrors

def sectors = 0..<6
def sec = { int s -> s+1 }

// define timeline graphs
def TL = sectors.collect { s ->
  def g = new GraphErrors("sector_"+sec(s))
  g.setTitle("Median Electron Trigger N/F")
  g.setTitleY("median N/F")
  g.setTitleX("run number")
  return g
}

// get list of outhipo files
def inHipoDir = new File("outhipo")
def inHipoList = []
inHipoDir.traverse(
  type: groovy.io.FileType.FILES,
  nameFilter: ~/mondata.*\.hipo/ )
{ inHipoList << inHipoDir.getName() + "/" + it.getName() }
inHipoList.sort()

def inHipoTdir
def runnum
def rundirN
def objList
def secnum
def med

def outHipoTdir = new TDirectory()


// loop through list of outhipo files
inHipoList.each { fileN ->
  println "======= concatenate " + fileN

  runnum = fileN.split('/')[-1].tokenize('.').get(1).toInteger()
  rundirN = "/${runnum}/"
  
  // get list of objects in this hipo file
  inHipoTdir = new TDirectory()
  inHipoTdir.readFile(fileN)
  objList = inHipoTdir.getCompositeObjectList(inHipoTdir)

  // make corresponding directory in output hipo file
  outHipoTdir.mkdir(rundirN)
  outHipoTdir.cd(rundirN)

  // loop through objects, adding them to the output hipo file
  objList.each { obj ->
    println "add $obj"
    outHipoTdir.addDataSet(inHipoTdir.getObject(obj))

    // if it's a median line, get its value and add a point to the timeline
    if(obj.endsWith(":mq")) {
      secnum = obj.split('/')[-1].tokenize('_:').get(3).toInteger() - 1
      if(sectors.find{it==secnum}==null) {
        System.err << "ERROR in secnum identification (secnum=${secnum})\n"
        return
      }
      med = inHipoTdir.getObject(obj).evaluate(0)
      TL[secnum].addPoint(runnum,med,0,0)
      println sec(secnum)+" "+med
    }

  }

  inHipoTdir = null // GC

}

// output
outHipoTdir.mkdir("/timelines")
outHipoTdir.cd("/timelines")
TL.each { outHipoTdir.addDataSet(it) }

def outHipoN = "00.electron_trigger_QA.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipoTdir.writeFile(outHipoN)

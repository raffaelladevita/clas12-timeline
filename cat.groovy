import org.jlab.groot.data.TDirectory
import static groovy.io.FileType.FILES
import org.jlab.groot.math.F1D
import org.jlab.groot.data.GraphErrors

def sectors = 0..<6
def sec = { int s -> s+1 }

// define timeline graphs
def TL = sectors.collect { s ->
  def g = new GraphErrors("sector_"+sec(s))
  g.setTitle("Electron Trigger QA Pass Fraction")
  g.setTitleY("QA pass fraction")
  g.setTitleX("run number")
  return g
}
def nGood = sectors.collect{0}
def nBad = sectors.collect{0}

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

def getSec = { name -> 
  def s = name.split('/')[-1].tokenize('_:')[3].toInteger() - 1 
  if(sectors.find{it==s}==null) System.err << "ERROR in getSec (s=${s})\n"
  return s
}


// loop through list of outhipo files
inHipoList.each { fileN ->
  println "======= concatenate " + fileN

  // reset good/bad file counters
  nGood = sectors.collect{0}
  nBad = sectors.collect{0}

  // get run number
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

    // get numbers of good and bad files
    if(obj ==~ /^.*gr_.*_sec_.$/) {
      secnum = getSec(obj)
      nGood[secnum] = inHipoTdir.getObject(obj).getDataSize(0)
    }
    else if(obj ==~ /^.*gr_.*_sec_.:outliers$/) {
      secnum = getSec(obj)
      nBad[secnum] = inHipoTdir.getObject(obj).getDataSize(0)
    }

    // if it's a median line, get its value and add a point to the timeline
    /*
    if(obj.endsWith(":mq")) {
      secnum = getSec(obj)
      med = inHipoTdir.getObject(obj).evaluate(0)
      TL[secnum].addPoint(runnum,med,0,0)
      println sec(secnum)+" "+med
    }
    */

  }


  // add points to timeline
  TL.eachWithIndex { t,s ->
    t.addPoint( 
      runnum,
      nBad[s]+nGood[s]>0 ? nGood[s]/(nGood[s]+nBad[s]) : 0,
      0,0
    )
  }



  // garbage collection
  inHipoTdir = null

}

// output
outHipoTdir.mkdir("/timelines")
outHipoTdir.cd("/timelines")
TL.each { outHipoTdir.addDataSet(it) }

def outHipoN = "00.electron_trigger_QA.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipoTdir.writeFile(outHipoN)

import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F


int runnumTmp = -1
boolean dwAppend = false
def sectors = 0..<6
def sector = { int i -> i+1 }

for(arg in args) {

  // open hipo file
  TDirectory dir = new TDirectory()
  dir.readFile(arg)


  // get run and file numbers
  def fname = arg.split('/')[-1].tokenize('_.')
  def runnum = fname[1].toInteger()
  def filenum = fname[2].toInteger()
  println "fname="+fname
  println "runnum="+runnum
  println "filenum="+filenum


  // define output datfile
  if(runnum!=runnumTmp || runnumTmp<0) {
    dwAppend = false
    runnumTmp = runnum
  } else dwAppend = true
  def datfile = new File("datfiles/mondata."+runnum+".dat")
  def dw = datfile.newWriter(dwAppend)
  

  // read electron trigger histograms and number of entries
  def heth = sectors.collect{ dir.getObject('/electron/trigger/heth_'+sector(it)) }
  def entries = { int i -> heth[i].integral() }
  sectors.each{ 
    outputdat = [ runnum, filenum, sector(it), entries(it) ] // <-- COLUMNS
    dw << outputdat.join(' ') << '\n'
  }
  dw.close()
  //print datfile.text
}



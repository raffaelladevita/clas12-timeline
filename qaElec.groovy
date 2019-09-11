import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F


//TDirectory out = new TDirectory()

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
  def datfileN = arg.split('/')[-1].replace('hipo','dat')
  def datfile = new File("datfiles/"+datfileN)
  def dw = datfile.newWriter()
  

  // read electron trigger histograms and number of entries
  def sectors = 0..<6
  def heth = sectors.collect{ dir.getObject('/electron/trigger/heth_'+(it+1)) }
  def ent = sectors.collect{ heth[it].integral() }
  sectors.each{ dw << (it+1) + " " + ent[it] + "\n" }
  dw.close()
  print datfile.text

  
  //out.mkdir('/'+run)
  //out.cd('/'+run)
  //out.addDataSet(h1)
  // out.addDataSet(f1)
}


//out.mkdir('/timelines')
//out.cd('/timelines')
//grtl.each{ out.addDataSet(it) }

//out.writeFile('rat_electron.hipo')

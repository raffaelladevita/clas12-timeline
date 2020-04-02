/* generate a table of data with:
 * - first argument is 'dataset', which specifies which set of monsub files to read
 *   (and where to write the resulting data table)
 * - number of electron triggers, from monsub files
 * - faraday cup charge, from fcdata.${dataset}.json
 */

import static groovy.io.FileType.FILES
import groovy.json.JsonSlurper
import org.jlab.groot.data.TDirectory

//----------------------------------------------------------------------------------
// ARGUMENTS
def dataset = 'fall18'
def garbageCollect = false
if(args.length>=1) dataset = args[0]
if(args.length>=2) garbageCollect = args[1].toInteger() == 1
def monsubDir = "monsub.${dataset}"
//----------------------------------------------------------------------------------


// get list of monsub hipo files
println "--- get list of monsub hipo files"
def monsubDirObj = new File(monsubDir)
def fileList = []
def fileFilter = ~/monplots_.*\.hipo/
monsubDirObj.traverse(
  type: groovy.io.FileType.FILES,
  nameFilter: fileFilter )
{ 
  if(it.size() > 0)  fileList << monsubDir+"/"+it.getName() 
  else println "[WARNING] skip empty file "+it.getName()
}
fileList.sort()
//fileList.each { println it }
println "--- list obtained"

// open faraday cup json
def fcFileName = "fcdata.${dataset}.json"
def slurp = new JsonSlurper()
def fcFile = new File(fcFileName)

// define vars and subroutines
def runnum, runnumTmp, filenum
def fcMapRun
def fcMapRunFiles
def fcVals
def ufcVals
def fcStart, fcStop
def ufcStart, ufcStop
def nTrig
def heth
def fileNtok
boolean success
def errPrint = { str -> 
  System.err << "ERROR in run ${runnum}_${filenum}: "+str+"\n" 
  success = false
}
def sectors = 0..<6
def sec = { int i -> i+1 }

// define output data_table.dat file
"mkdir -p outdat.${dataset}".execute()
def datfile = new File("outdat.${dataset}/data_table.dat")
def datfileWriter = datfile.newWriter(false)

// loop through input hipo files
//----------------------------------------------------------------------------------
TDirectory tdir
runnumTmp=0

//fileList = fileList.subList(0,10); // (read only a few files, for fast testing)

println "---- BEGIN READING FILES"
fileList.each{ fileN ->

  println "-- READ: "+fileN
  success = true

  // get run number and file number
  fileNtok = fileN.split('/')[-1].tokenize('_.')
  runnum = fileNtok[1].toInteger()
  filenum = fileNtok[2].toInteger()

  // if runnum changed, obtain this run's faraday cup data
  if(runnum!=runnumTmp) {
    fcMapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
    if(!fcMapRun) throw new Exception("run ${runnum} not found in "+fcFileName);
    runnumTmp = runnum
  }

  // read faraday cup info for this runfile
  if(fcMapRun) fcMapRunFiles = fcMapRun.groupBy{ it.fnum }.get(filenum)
  if(fcMapRunFiles) {
    // "gated" and "ungated" were switched in hipo files...
    fcVals=fcMapRunFiles.find()."data"."fcup" // actually gated
    ufcVals=fcMapRunFiles.find()."data"."fcupgated" // actually ungated
  } else errPrint("faraday cup values not found in "+fcFileName)
  if(fcVals && ufcVals) {
    fcStart = fcVals."min"
    fcStop = fcVals."max"
    ufcStart = ufcVals."min"
    ufcStop = ufcVals."max"
    //println "fcStart="+fcStart+" fcStop="+fcStop
  } else errPrint("faraday cup values not found in "+fcFileName)
  if(fcStart>fcStop || ufcStart>ufcStop) errPrint("faraday cup start > stop");

  // open hipo file get electron trigger histograms (for obtaining num. triggers)
  tdir = new TDirectory()
  tdir.readFile(fileN)
  heth = sectors.collect{ tdir.getObject('/electron/trigger/heth_'+sec(it)) }
  sectors.each{ if(heth[it]==null) errPrint("missing histogram in sector "+sec(it)) }

  // if no errors thrown above, continue analyzing
  if(success) {

    // get number of electron triggers
    nTrig = { int i -> heth[i].integral() }

    // output to datfile (the zero is for the FT electron count, to maintain
    //                    compatibility with updated downstream code)
    sectors.each{
      datfileWriter << [ runnum, filenum, sec(it), nTrig(it), 0 ].join(' ') << ' '
      datfileWriter << [ fcStart, fcStop, ufcStart, ufcStop ].join(' ') << '\n'
    }

    // force garbage collection (if you want)
    tdir = null
    if(garbageCollect) System.gc()

  } // eo if(success)
} // eo loop over hipo files
println "--- done reading hipo files"

// close buffer writers
datfileWriter.close()

// for testing memory usage

import org.jlab.io.hipo.HipoDataSource

// ARGUMENTS
def infile = "dst.hipo"
if(args.length>=1) infile = args[0]
println "infile=$infile"

// define variables
def event
def evCount

// open hipo reader
reader = new HipoDataSource()
reader.open(infile)

// event loop
evCount = 0
while(reader.hasEvent()) {
  evCount++
  if(evCount % 100000 == 0) println "analyzed $evCount events"
  event = reader.getNextEvent()
}
reader.close()

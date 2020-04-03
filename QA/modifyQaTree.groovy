import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.Date
import Tools
Tools T = new Tools()


// check arguments / print help
def usage = [:]
usage["sectorLoss"] = 
"""
sectorLoss [run] [firstFile] [lastFile] [list_of_sectors]
  - set [lastFile] to zero to denote last file of run
  - [list_of_sectors] will add the down sectors as a comment
"""
def syntaxError = { key ->
  println("ERROR: syntax for $key arguments")
  println(usage[key])
}
println("\n\n")


def cmd
if(args.length>=1) cmd = args[0]
else { 
  print(
  """
  syntax: groovy modifyQaTree.groovy [command] [arguments]
  commands [arguments]: 
  """)
  usage.each{ println(it.value) }
}



// backup qaTree.json
def D = new Date()
("cp qa/qaTree.json qa/qaTree.json."+D.getTime()+".bak").execute()


// open qaTree.json
infile="qa/qaTree.json"
def slurper = new JsonSlurper()
def jsonFile = new File(infile)
def qaTree = slurper.parse(jsonFile)


// CMD sectorLoss
if(cmd=="sectorLoss") {
  def rnum,fnumL,fnumR
  def secList = []
  if(args.length>4) {
    rnum = args[1].toInteger()
    fnumL = args[2].toInteger()
    fnumR = args[3].toInteger()
    (4..<args.length).each{ secList<<args[it].toInteger() }

    def cmt = secList.size() > 0 ? 
      "sectors "+secList.join(" ")+" diminished" : ""
    println("set run $rnum files ${fnumL}-"+
      (fnumR>0 ? fnumR : "END") + " to defect=sectorLoss :: $cmt"
    )

    qaTree["$rnum"].each { k,v ->
      def qaFnum = k.toInteger()
      if( qaFnum>=fnumL && ( fnumR==0 || qaFnum<=fnumR ) ) {
        qaTree["$rnum"]["$qaFnum"]["defect"] = (0x1 << T.bitSectorLoss)
        (1..6).each{ 
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] = 
            it in secList ? [T.bitSectorLoss] : []
        }
        if(secList.size()>0) qaTree["$rnum"]["$qaFnum"]["comment"] = cmt
      }
    }
  }
  else syntaxError(cmd)
}

else { println("ERROR: unknown command!"); return }


// update qaTree.json
new File("qa/qaTree.json").write(JsonOutput.toJson(qaTree))
"groovy parseQaTree.groovy".execute()

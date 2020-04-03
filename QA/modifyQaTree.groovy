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
usage["setBit"] = "setBit: run with no further arguments to print details\n"
usage["addBit"] = "addBit: run with no further arguments to print details\n"
usage["delBit"] = "delBit: run with no further arguments to print details\n"

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
  return
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
      "sector(s) "+secList.join(" ")+" diminished" : ""
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
  else {
    syntaxError(cmd)
    return
  }
}


else if( cmd=="setBit" || cmd=="addBit" || cmd=="delBit") {
  def rnum,fnumL,fnumR
  def secList = []
  if(args.length>5) {
    bit = args[1].toInteger()
    rnum = args[2].toInteger()
    fnumL = args[3].toInteger()
    fnumR = args[4].toInteger()
    if(args[5]=="all") secList = (1..6).collect{it}
    else (5..<args.length).each{ secList<<args[it].toInteger() }

    println("run $rnum files ${fnumL}-"+(fnumR>0 ? fnumR : "END") + 
      " sectors ${secList}: $cmd $bit")

    println("Enter a comment, if you want, otherwise press return")
    print("> ")
    def cmt = System.in.newReader().readLine()


    qaTree["$rnum"].each { k,v ->
      def qaFnum = k.toInteger()
      if( qaFnum>=fnumL && ( fnumR==0 || qaFnum<=fnumR ) ) {

        if(cmd=="setBit") {
          (1..6).each{ 
            qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] = 
              it in secList ? [bit] : []
          }
        }
        else if(cmd=="addBit") {
          secList.each{
            qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] += bit
          }
        }
        else if(cmd=="delBit") {
          secList.each{
            qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] -= bit
          }
        }

        defList = []
        defMask = 0
        (1..6).each{ s -> 
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$s"].unique()
          defList +=
            qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$s"].collect{it.toInteger()}
        }
        defList.unique().each { defMask += (0x1<<it) }
        qaTree["$rnum"]["$qaFnum"]["defect"] = defMask

        if(cmt.length()>0 || cmd=="setBit") 
          qaTree["$rnum"]["$qaFnum"]["comment"] = cmt
      }
    }

  }
  else {
    def helpStr
    if(cmd=="setBit") {
      helpStr = "  - overwrites ALL stored defectBit(s) with specified bit"
    }
    else if(cmd=="addBit") helpStr = "  - add specified bit to defectBit(s)"
    else if(cmd=="delBit") helpStr = "  - delete specified bit from defectBit(s)"
    println(
    """
    SYNTAX: ${cmd} [defectBit] [run] [firstFile] [lastFile] [list_of_sectors]
    ${helpStr}
      - set [lastFile] to zero to denote last file of run
      - use \"all\" in place of [list_of_sectors] to apply to all sectors
    """)
    println("grep bit Tools.groovy".execute().in.text)
    return
  }
}


else { println("ERROR: unknown command!"); return }


// update qaTree.json
new File("qa/qaTree.json").write(JsonOutput.toJson(qaTree))
"groovy parseQaTree.groovy".execute()

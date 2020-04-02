// parses qa/qaTree.json into human readable format

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import Tools
Tools T = new Tools()

def slurper = new JsonSlurper()
def jsonFile = new File("qa/qaTree.json")
def qaTree = slurper.parse(jsonFile)
qaTree.sort{a,b -> a.key.toInteger() <=> b.key.toInteger() }.each{
  run, runTree ->
  runTree.sort{a,b -> a.key.toInteger() <=> b.key.toInteger() }.each{
    file, fileTree ->
    defect = fileTree.defect
    println("$run $file $defect")
  }
}

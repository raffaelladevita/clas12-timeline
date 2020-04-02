if(args.size()==0) {
  println("\n\nARGUMENTS: jsonfile [tree-path]")
  println("""
  pretty prints a json file
  - if it is a nested map (tree), specify additional arguments
    to restrict printout to a specific sub-tree
  """)
  return
}

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def slurper = new JsonSlurper()
def jsonFile = new File(args[0])
def jsonObj = slurper.parse(jsonFile)

def jAccess ( tree,path ) {
  if(path.size()<1) println JsonOutput.prettyPrint(JsonOutput.toJson(tree))
  else if(path.size()==1) jAccess(tree[path[0]],[])
  else jAccess(tree[path[0]],path[1..-1])
}

def accessPath = []
args.length.times { if(it>=1) accessPath << args[it] }
println("\n\n$accessPath\n\n")
jAccess(jsonObj,accessPath)

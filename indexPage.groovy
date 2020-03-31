// generate ListOfTimelines.json file, for hipo files in the online directory's
// subirectories
import static groovy.io.FileType.FILES
import groovy.json.JsonOutput

def wwwDirName="../www"
if(args.length>=1) wwwDirName = args[0]

def wwwDir = new File(wwwDirName)
def datasetList = []
wwwDir.traverse(
  type: groovy.io.FileType.DIRECTORIES ) 
{ datasetList << it.getName() }
datasetList.sort()
println datasetList

def fileTreeList = []
def fileTree = [:]
def datasetDir
def timelineList = []
datasetList.each { datasetDirName ->
  timelineList = []
  fileTree = [:]
  fileTree.put('subsystem',datasetDirName)

  datasetDir = new File("${wwwDirName}/${datasetDirName}")
  datasetDir.traverse(
    type: groovy.io.FileType.FILES,
    nameFilter: ~/.*\.hipo/ )
  { 
    timelineList << it.getName().replaceAll(/\.hipo$/,"")
  }

  fileTree.put('variables',timelineList)
  fileTreeList << fileTree
}

//println JsonOutput.prettyPrint(JsonOutput.toJson(fileTreeList))
new File("${wwwDirName}/ListOfTimelines.json").write(JsonOutput.toJson(fileTreeList))

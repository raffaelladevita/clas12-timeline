// test code for reading fcdata.json
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def slurp = new JsonSlurper()
def fcFile = new File("fcdata.json")

int runnum = 5032
int filenum = 60

def jprint = { map -> println JsonOutput.prettyPrint(JsonOutput.toJson(map)) }

def mapRun = slurp.parse(fcFile).groupBy{ it.run }
def mapRunFiles = mapRun.get(runnum).groupBy{ it.fnum }
def fcVals = mapRunFiles.get(filenum).find()."data"."fc"

//jprint(mapRun)
//jprint(mapRunFiles)
//jprint(fcVals)

println fcVals."fcmax"
println fcVals."fcmin"

// test code for reading fcdata.json

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def fcFileName = "fcdata.json"

def slurp = new JsonSlurper()
def fcFile = new File(fcFileName)

int runnum = 5129
int filenum = 375

def jprint = { map -> println JsonOutput.prettyPrint(JsonOutput.toJson(map)) }

def mapRun
def mapRunFiles
def fcVals

mapRun = slurp.parse(fcFile).groupBy{ it.run }.get(runnum)
if(mapRun) mapRunFiles = mapRun.groupBy{ it.fnum }.get(filenum)
if(mapRunFiles) fcVals = mapRunFiles.find()."data"."fc"

if(fcVals) {
  //jprint(mapRun)
  //jprint(mapRunFiles)
  //jprint(fcVals)
  println fcVals."fcmin"
  println fcVals."fcmax"
} else throw new Exception("run ${runnum}_${filenum} not found in "+fcFileName)


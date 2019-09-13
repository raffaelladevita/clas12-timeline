// pretty prints a json file

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def s = new JsonSlurper()
def f = new File(args[0])
println JsonOutput.prettyPrint(JsonOutput.toJson(s.parse(f)))

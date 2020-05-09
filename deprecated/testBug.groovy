// test Tools::exeLeaves bug
// there is a difference between groovy 2.5 and 2.4:
// "push" and "pop" operations on lists are different between the two versions
// - this has been fixed
import org.jlab.groot.data.H1F
import org.jlab.groot.data.H2F
import groovy.json.JsonOutput
import Tools // (make sure `.` is in $CLASSPATH)
Tools T = new Tools()
def pPrint = { str -> JsonOutput.prettyPrint(JsonOutput.toJson(str)) }

println("CALL buildTree")
def partList = [ 'pip', 'pim' ]
def helList = [ 'hp', 'hm' ]
def histTree = [:]
T.buildTree(histTree,'helic',[
  ['sinPhi'],
  partList,
  helList
],{ /*new H1F()*/ })
println("PRINT histTree branches")
T.printTree(histTree)
println("JSON PRINT:")
println pPrint(histTree)


def buildHist(histName, histTitle, propList, runn, nb, lb, ub, nb2=0, lb2=0, ub2=0) {

  def propT = [ 
    'pip':'pi+',
    'pim':'pi-', 
    'hp':'hel+',
    'hm':'hel-',
    'hu':'hel?'
  ]

  def pn = propList.join('_')
  def pt = propList.collect{ propT.containsKey(it) ? propT[it] : it }.join(' ')
  if(propList.size()>0) { pn+='_'; }

  def sn = propList.size()>0 ? '_':''
  def st = propList.size()>0 ? ' ':''
  def hn = "${histName}_${pn}${runn}"
  def ht = "${pt} ${histTitle}"

  if(nb2==0) return new H1F(hn,ht,nb,lb,ub)
  else return new H2F(hn,ht,nb,lb,ub,nb2,lb2,ub2)
}

println("CALL exeLeaves buildHist")
def runnum = 1000
def nbins = 100
T.exeLeaves( histTree.helic.sinPhi, {
  T.leaf = buildHist('helic_sinPhi','sinPhiH',T.leafPath,runnum,nbins,-1,1) 
})
println("PRINT tree histogram names and titles")
T.printTree(histTree,{T.leaf.getName()+";; "+T.leaf.getTitle()})
println("TESTED ON\n"+"groovy -v".execute().text)

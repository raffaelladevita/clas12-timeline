import org.jlab.clas.physics.LorentzVector
import org.jlab.clas.physics.Vector3
import groovy.json.JsonOutput

class Tools {

  //////////
  // MATH //
  //////////

  // calculate scalar product of 4-vectors
  def lorentzDot = { v1,v2 -> return v1.e()*v2.e() - v1.vect().dot(v2.vect()) }

  // calculate angle between two planes (used for PhiH)
  def crossAB,crossCD
  def sgn
  def numer
  def denom
  def planeAngle = { vA,vB,vC,vD ->
    crossAB = vA.cross(vB) // AxB
    crossCD = vC.cross(vD) // CxD

    // calculate sign of (AxB).D
    sgn = crossAB.dot(vD) // (AxB).D
    if(Math.abs(sgn)<0.00001) return -10000
    sgn /= Math.abs(sgn)

    // calculate numerator and denominator 
    numer = crossAB.dot(crossCD) // (AxB).(CxD)
    denom = crossAB.mag() * crossCD.mag() // |AxB|*|CxD|
    if(Math.abs(denom)<0.00001) return -10000

    // return angle
    return sgn * Math.acos(numer/denom)
  }


  ///////////
  // TREES //
  ///////////

  // build a tree recursively, with branch levels specified by 'levelList'
  // - the leaves will be set to the result of the closure 'clos'
  // - call the subroutine 'buildTree' to initiate
  def rec_buildTree(tree, levelList, lev, clos) {
    def levNew = lev
    levelList.each{ branches ->
      if(lev==levNew) {
        branches.each{ branch ->
          if(levelList.size()==1) { tree[branch] = clos() }
          else {
            tree[branch] = [:]
            levNew = rec_buildTree(tree[branch],levelList[1..-1],lev+1,clos)
          }
        }
      }
    }
    return lev
  }

  // builds a tree called 'treeName', branching from 'trunkName' the branches will be
  // built according to 'levelList'
  // - each element of levelList is a branch list, wherein each branch will split into
  //   the branches listed in the subsequent element of levelList; the last element of
  //   levelList refers to the leaves
  // - the leaves will be initialized with the result of the closure 'clos'
  def buildTree = { trunkName, treeName, levelList, clos -> 
    rec_buildTree( trunkName, [[treeName],*levelList], 0, clos )
  }


  // get a leaf, following the path of branch names specified 
  // by the list 'path'
  def getLeaf = { tree,path ->
    def node = tree
    path.each { node = node[it] }
    return node
  }

  // execute a closure on all leaves which stem from a given node
  // - set 'node' to the top level node of the (sub)tree
  // - set 'clos' to the closure, wherein you may use the variables 'this.leaf' and
  //   'this.leafPath'
  def leaf
  def leafPath
  def exeLeaves( node, clos, path=[] ) {
    // if the current node has branches, it is not a leaf; loop through the branches and
    // analyze their nodes
    if(node.getClass()==java.util.LinkedHashMap) {
      node.each { 
        path.push(it.key)
        exeLeaves(it.value,clos,path)
        if(it.value.getClass()!=java.util.LinkedHashMap) {
          it.value = leaf
        }
        path.pop()
      }
    }
    else {
      // this is a leaf, since there are no branches;
      // -> execute the closure
      leaf = node
      leafPath = path
      clos()
    }
  }


  // pretty print a tree
  /*
  def printTree = { tree ->
    println new groovy.json.JsonBuilder(tree).toPrettyString()
  }
  */
  def printTree(tree,clos={""}) { exeLeaves(tree,{println "$leafPath "+clos()}) }

    
}

package org.jlab.clas.timeline.timeline.htcc
import java.util.concurrent.ConcurrentHashMap
import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors

class htcc_nphe_ring_sector {

def data = new ConcurrentHashMap()

def processDirectory(dir, run) {
  def histlist =   (0..<6).collect{s->
    (0..<4).collect{r->
      def h1 = dir.getObject(String.format('/HTCC/H_HTCC_nphe_s%d_r%d_side1',s+1,r+1)) //left
      def h2 = dir.getObject(String.format('/HTCC/H_HTCC_nphe_s%d_r%d_side2',s+1,r+1)) //right
      h1.add(h2)
      h1.setName("sec"+(s+1) +"ring"+(r+1))
      h1.setTitle("HTCC Number of Photoelectrons")
      h1.setTitleX("HTCC Number of Photoelectrons")
      return h1
    }
  }
  data[run] = [run:run, hlist:histlist]
}



def close() {

  TDirectory out = new TDirectory()
  out.mkdir('/timelines')
  (0..<6).each{ sec->
    (0..<4).each{ ring ->
      def grtl = new GraphErrors('sec'+(sec+1)+' ring'+(ring+1))
      grtl.setTitle("Average HTCC Number of Photoelectrons per sector per ring")
      grtl.setTitleY("Average HTCC Number of Photoelectrons per sector per ring")
      grtl.setTitleX("run number")

      data.sort{it.key}.each{run,it->
        if (sec==0 && ring ==0){
          out.mkdir('/'+it.run)
        }
        out.cd('/'+it.run)
        out.addDataSet(it.hlist[sec][ring])
        grtl.addPoint(it.run, it.hlist[sec][ring].getMean(), 0, 0)
      }
      out.cd('/timelines')
      out.addDataSet(grtl)
    }
  }

  out.writeFile('htcc_nphe_sec_ring.hipo')
}
}

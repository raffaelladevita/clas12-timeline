package org.jlab.clas.timeline.timeline.htcc
import java.util.concurrent.ConcurrentHashMap
import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.clas.timeline.fitter.HTCCFitter

class htcc_vtimediff {

  def data = new ConcurrentHashMap()

  def processDirectory(dir, run) {
    def funclist = []
    def avglist = []
    def meanlist = []
    def sigmalist = []
    def chi2list = []

    def histlist =   (0..<6).collect{s->
      (0..<4).collect{r->
        (0..<2).collect{side->
          def h1 = dir.getObject(String.format("/HTCC/H_HTCC_vtime_s%d_r%d_side%d",s+1,r+1,side+1))
          def f1 = HTCCFitter.timeIndPMT(h1)
          funclist.add(f1)
          avglist.add(h1.getMean())
          meanlist.add(f1.getParameter(1))
          sigmalist.add(f1.getParameter(2).abs())
          chi2list.add(f1.getChiSquare())
          return h1
        }
      }
      println("debug: "+run)
      data[run] = [run:run, hlist:histlist, flist: funclist, average: avglist, mean: meanlist, sigma:sigmalist]
    }
  }



  def close() {

    ['mean', 'sigma'].each{name ->
      TDirectory out = new TDirectory()
      out.mkdir('/timelines')
      (0..<6).each{ sec->
        (0..<4).each{ ring ->
          (0..<2).each{ side ->
            def grtl = new GraphErrors('sec'+(sec+1)+' ring'+(ring+1)+' side'+(side+1))
            grtl.setTitle("HTCC vtime - STT, electrons")
            grtl.setTitleY("HTCC vtime - STT, electrons, per PMTs (ns)")
            grtl.setTitleX("run number")

            data.sort{it.key}.each{run,it->
              if (sec==0 && ring==0 && side==0){
                out.mkdir('/'+it.run)
              }
              out.cd('/'+it.run)
              out.addDataSet(it.hlist[sec][ring][side])
              grtl.addPoint(it.run, it.hlist[sec][ring][side].getMean(), 0, 0)
            }
            out.cd('/timelines')
            out.addDataSet(grtl)
          }
        }
      }
    }
    out.writeFile('htcc_vtimediff_'+name+'.hipo')
  }
}

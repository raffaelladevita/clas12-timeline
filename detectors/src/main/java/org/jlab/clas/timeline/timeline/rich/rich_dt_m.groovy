package org.jlab.clas.timeline.timeline.rich
import java.util.concurrent.ConcurrentHashMap
import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.clas.timeline.fitter.RICHFitter;

class rich_dt_m {


def data = new ConcurrentHashMap()

def processDirectory(dir, run) {
  def funclist = []
  def meanlist = []
  def sigmalist = []
  def chi2list = []
  def histlist =   (1..2).collect{
    def h1 = dir.getObject('/RICH/H_RICH_dt_m'+(it))
    def f1 = RICHFitter.timefit(h1)

    funclist.add(f1)
    meanlist.add(f1.getParameter(1))
    sigmalist.add(f1.getParameter(2).abs())
    chi2list.add(f1.getChiSquare())
    return h1
  }
  data[run] = [run:run, hlist:histlist, flist:funclist, mean:meanlist, sigma:sigmalist, clist:chi2list]
}



def close() {


  ['mean', 'sigma'].each{ name ->
    TDirectory out = new TDirectory()
    out.mkdir('/timelines')
    (1..2).each{ module->
      def grtl = new GraphErrors('module'+(module))
      grtl.setTitle("RICH #Delta (" + name + ") per module all the channels")
      grtl.setTitleY("RICH #Delta (" + name + ") per module all the channels (ns)")
      grtl.setTitleX("run number")

      data.sort{it.key}.each{run,it->
        if (module==1) out.mkdir('/'+it.run)
        out.cd('/'+it.run)
        out.addDataSet(it.hlist[module])
        out.addDataSet(it.flist[module])
        grtl.addPoint(it.run, it[name][module], 0, 0)
      }
      out.cd('/timelines')
      out.addDataSet(grtl)
    }

    out.writeFile('rich_dt_m_'+name+'.hipo')
  }

}
}

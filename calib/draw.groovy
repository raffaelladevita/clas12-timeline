// for testing plot drawing

import org.jlab.groot.data.TDirectory
import org.jlab.groot.ui.TCanvas 

def inTdir = new TDirectory()
inTdir.readFile('../../www/dilks/calib/ctof/ctof_edep_QA.hipo')
def graph = inTdir.getObject('/timelines/Edep')
def line1 = inTdir.getObject('/timelines/plotLine:horizontal:5.7:gray')
def line2 = inTdir.getObject('/timelines/plotLine:horizontal:6.3:gray')
def canv = new TCanvas("canv",1000,1000)
canv.draw(graph)
canv.draw(line1,'same')
canv.draw(line2,'same')

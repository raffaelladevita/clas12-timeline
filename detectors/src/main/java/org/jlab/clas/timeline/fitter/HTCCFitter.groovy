/**
*
* Fitter package for HTCC
*
* Writer: Sangbaek Lee, Raffaella De Vita
*
**/
package org.jlab.clas.timeline.fitter
import org.jlab.groot.fitter.DataFitter
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D


class HTCCFitter{
	static F1D timeIndPMT(H1F h1) {
        int nBinsTime = 4000;
        double lowTime = -500;
        double highTime = 500;
        def f1 = new F1D("fit:"+h1.getName() + t, "[amp]*gaus(x,[mean],[sigma])", -500, 500);
        // f1.setParameter(0, 500);
        // f1.setParameter(1, -0.0);
        // f1.setParameter(2, 0.7);
        f1.setLineColor(2);
        f1.setLineWidth(2);
        f1.setOptStat(1101);
        double maxV = h1.getMaximumBin();
        maxV = lowTime + (maxV + 0.5) * (highTime - lowTime) / nBinsTime;
        f1.setParameter(0, h1.getMax());
        f1.setParameter(1, maxV);
        f1.setParameter(2, 0.6);
        f1.setRange(maxV - 1, maxV + 1.3);
        DataFitter.fit(f1, h1, "");

		return f1
 	}

 	static F1D timeAllFit(H1F h1){
        F1D f1 = new F1D("fit:"+h1.getName(), "[amp]*gaus(x,[mean],[sigma])", -1, 1);
        f1.setRange(-1, 1);
        f1.setParameter(0, 20000);
        f1.setParameter(1, 0);
        f1.setParameter(2, 1);
        f1.setLineColor(2);
        f1.setLineWidth(2);
        f1.setOptStat("1100");
        DataFitter.fit(f1, h1, "");
		return f1
	}
}
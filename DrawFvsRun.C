// draw faraday cup charge vs. run number
void DrawFvsRun() {
  TFile * f = new TFile("tree.root");
  TTree * t = (TTree*) f->Get("tr");
  TH1D * h = new TH1D("h","F vs. run;run;F",300,5000,5300);
  t->Project("h","runnum","(fcstop-fcstart)*(sector==1)");
  new TCanvas(); h->Draw("hist");
  for(int b=1; b<h->GetNbinsX(); b++) {
    if(h->GetBinContent(b)>0) 
      printf("%d %ld\n",
        (int) h->GetBinLowEdge(b),
        (long) h->GetBinContent(b));
  }
};


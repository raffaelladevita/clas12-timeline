// draw faraday cup charge and numElectronTriggers vs. run number
void DrawVsRun() {

  TFile * f = new TFile("tree.root");
  TTree * t = (TTree*) f->Get("tr");

  Int_t binning[3] = { 300, 5000, 5300 };
  TH1D * hF = new TH1D("hF","F vs. run;run;F",binning[0],binning[1],binning[2]);
  TH1D * hN[6]; 
  TString hName[6];
  TString hCut[6];
  int s;
  for(s=0; s<6; s++) {
    hName[s] = Form("hN%d",s+1);
    hCut[s] = Form("sector==%d",s+1);
    hN[s] = new TH1D(hName[s],TString(hCut[s]+": N vs. run;run;N",s+1),
                     binning[0],binning[1],binning[2]);
  };

  t->Project("hF","runnum","(fcstop-fcstart)*(sector==1)");
  for(s=0; s<6; s++) t->Project(hName[s],"runnum",TString("ntrig*("+hCut[s]+")"));

  //new TCanvas(); hF->Draw("hist");
  //for(s=0; s<6; s++) { new TCanvas(); hN[s]->Draw("hist"); };

  for(int b=1; b<binning[0]; b++) {
    if(hF->GetBinContent(b)>0) {
      printf("%d %ld", (int) hF->GetBinLowEdge(b), (long) hF->GetBinContent(b) );
      for(s=0; s<6; s++) printf(" %ld", (long) hN[s]->GetBinContent(b) );
      printf("\n");
    };
  }
};


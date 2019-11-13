// called by mkTree.sh
//
void readTree() {

  // open root file
  gStyle->SetOptStat(0);
  TFile * f = new TFile("tree.root","RECREATE");
  TTree * tr = new TTree("tr","tr");
  TString cols = "i/I:runnum/I:filenum/I:sector/I:ntrig/F:fcstart/F:fcstop/F:nf/F";
  tr->ReadFile("tree.tmp",cols);
  //Double_t maxLineY = tr->GetMaximum("nf");
  //Double_t maxLineY = 4;
  Double_t maxLineY = 16000;

  // draw epoch lines
  // - green line is start of epoch
  // - red line is end of epoch 
  // - lines are shifted so they are drawn in bin centers
  const Int_t maxN = 50;
  TLine * eLine[2][maxN];
  int n=0;
  TTree * etr = new TTree("etr","etr");
  etr->ReadFile("epochs.txt","lb/I:ub/I");
  Int_t e[2];
  int color[2] = {kGreen+1,kRed};
  etr->SetBranchAddress("lb",&e[0]);
  etr->SetBranchAddress("ub",&e[1]);
  for(int x=0; x<etr->GetEntries(); x++) {
    etr->GetEntry(x);
    for(int j=0; j<2; j++) {
      eLine[j][n] = new TLine(e[j]+0.5,0,e[j]+0.5,maxLineY);
      eLine[j][n]->SetLineColor(color[j]);
      eLine[j][n]->SetLineWidth(3);
    };
    n++;
  };


  // draw everything
  TCanvas * c[6];
  TString cN,cut,rundrawNF,rundrawF;
  for(int s=0; s<6; s++) {
    cN = Form("sector%d",s+1);
    cut = Form("sector==%d",s+1);
    rundrawNF = Form("nf:runnum>>rNF%d(300,5000,5300,200,0,5)",s+1);
    rundrawF = Form("fcstop-fcstart:runnum>>rF%d(300,5000,5300,300,0,2700)",s+1);
    c[s] = new TCanvas(cN,cN,800,800);
    c[s]->Divide(2,2);
    for(int p=1; p<=4; p++) c[s]->GetPad(p)->SetGrid(0,1);
    c[s]->cd(1);
      tr->Draw("nf:i",cut,"*");
    c[s]->cd(2);
      tr->Draw(rundrawNF,cut,"colz");
      c[s]->GetPad(2)->SetLogz();
      for(int k=0; k<n; k++) for(int j=0; j<2; j++) eLine[j][k]->Draw("same");
    c[s]->cd(3);
      tr->Draw("fcstop-fcstart:i",cut,"*");
    c[s]->cd(4);
      tr->Draw(rundrawF,cut,"colz");
      c[s]->GetPad(4)->SetLogz();
      for(int k=0; k<n; k++) for(int j=0; j<2; j++) eLine[j][k]->Draw("same");
    c[s]->Write();
  };
  tr->Write();

  // study sector 1's increased N/F in epoch 3
  /*
  new TCanvas();
  tr->Draw("ntrig/(fcstop-fcstart):runnum>>a(300,5000,5300,100,0,5)","sector==1","colz");
  */
};

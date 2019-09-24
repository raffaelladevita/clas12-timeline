void readTree() {
  TFile * f = new TFile("tree.root","RECREATE");
  TTree * tr = new TTree("tr","tr");
  TString cols = "i/I:runnum/I:filenum/I:sector/I:ntrig/F:fcstart/F:fcstop/F:nf/F";
  tr->ReadFile("tree.tmp",cols);
  //Double_t maxNF = tr->GetMaximum("nf");
  Double_t maxNF = 4;

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
      eLine[j][n] = new TLine(e[j],0,e[j],maxNF);
      eLine[j][n]->SetLineColor(color[j]);
      eLine[j][n]->SetLineWidth(3);
    };
    n++;
  };


  TCanvas * c[6];
  TString cN,cut;
  for(int s=0; s<6; s++) {
    cN = Form("sector%d",s+1);
    cut = Form("sector==%d",s+1);
    c[s] = new TCanvas(cN,cN,800,800);
    c[s]->Divide(2,1);
    for(int p=1; p<=2; p++) c[s]->GetPad(p)->SetGrid(0,1);
    c[s]->cd(1);
    tr->Draw("nf:i",cut,"*");
    c[s]->cd(2);
    tr->Draw("nf:runnum",cut,"*");
    for(int k=0; k<n; k++) for(int j=0; j<2; j++) eLine[j][k]->Draw("same");
    c[s]->Write();
  };
  tr->Write();
};

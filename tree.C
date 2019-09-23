void tree() {
  TFile * f = new TFile("tree.root","RECREATE");
  TTree * tr = new TTree("tr","tr");
  TString cols = "i/I";
  cols += ":runnum/I";
  cols += ":filenum/I";
  cols += ":sector/I";
  cols += ":ntrig/F";
  cols += ":fcstart/F";
  cols += ":fcstop/F";
  cols += ":nf/F";
  tr->ReadFile("tree.tmp",cols);
  TCanvas * c[6];
  TString cN,cut;
  for(int s=0; s<6; s++) {
    cN = Form("sector%d",s+1);
    cut = Form("sector==%d",s+1);
    c[s] = new TCanvas(cN,cN,800,800);
    //tr->Draw("nf:i",cut,"*");
    tr->Draw("nf:runnum",cut,"*");
    c[s]->Write();
  };
  tr->Write();
};

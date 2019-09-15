void mkTree(Int_t runnum=5032) {
  TTree * tr = new TTree("tr","tr");
  gROOT->ProcessLine(".! cat outdat/mon*.dat > outdat/all.dat");
  TString bd = "run/I";
  bd += ":file/I";
  bd += ":sec/I";
  bd += ":nelec/F";
  bd += ":fcmin/F";
  bd += ":fcmax/F";
  tr->ReadFile("outdat/all.dat",bd);
  TString c = Form("run==%d && sec==1",runnum);
  new TCanvas(); tr->Draw("nelec/(fcmax-fcmin):file",c,"*");
  new TCanvas(); tr->Draw("nelec/(fcmax-fcmin)",c);
  new TCanvas(); tr->Draw("nelec:file",c,"*");
  new TCanvas(); tr->Draw("nelec",c);
};

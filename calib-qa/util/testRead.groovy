import org.jlab.groot.data.TDirectory
def hipoFile = "/u/group/clas/www/clas12mon/html/hipo/rga/pass0/v2.2.29/dc/dc_residuals_sec_mean.hipo"
if(args.length>=1) hipoFile = args[0]
def inTdir = new TDirectory()
inTdir.readFile(hipoFile)

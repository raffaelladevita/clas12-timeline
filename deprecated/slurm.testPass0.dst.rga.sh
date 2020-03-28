#!/bin/bash

#SBATCH --job-name=clasqa
#SBATCH --account=clas12
#SBATCH --partition=production

#SBATCH --mem-per-cpu=6000
#SBATCH --time=4:00:00

#SBATCH --array=0-9
#SBATCH --ntasks=1

#SBATCH --output=/farm_out/%u/%x-%j-%N.out
#SBATCH --error=/farm_out/%u/%x-%j-%N.err

dataList=(/lustre19/expphy/volatile/clas12/rg-a/production/pass0/physTrain/dst/recon/00*)

srun groovy monitorRead.groovy ${dataList[$SLURM_ARRAY_TASK_ID]} dst



#!/bin/bash

#SBATCH --job-name=clasqa
#SBATCH --account=clas12
#SBATCH --partition=production

#SBATCH --mem-per-cpu=6000
#SBATCH --time=1:30:00

#SBATCH --array=0-4
#SBATCH --ntasks=1

#SBATCH --output=/farm_out/%u/%x-%j-%N.out
#SBATCH --error=/farm_out/%u/%x-%j-%N.err

dataList=(/volatile/clas12/rg-k/production/pass0/physTrain/dst/train/skim4/*)

srun groovy monitorRead.groovy ${dataList[$SLURM_ARRAY_TASK_ID]} skim


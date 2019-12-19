#!/bin/bash

#SBATCH --job-name=clasqa_helicityPhi
#SBATCH --account=clas12
#SBATCH --partition=production

#SBATCH --mem-per-cpu=6000
#SBATCH --time=1:30:00

#SBATCH --array=0-10
#SBATCH --ntasks=1

#SBATCH --output=/farm_out/%u/%x-%j-%N.out
#SBATCH --error=/farm_out/%u/%x-%j-%N.err

dataList=(/cache/clas12/rg-a/production/reconstructed/Fall2018/Torus+1/pass1/v1/*)

srun groovy helicityPhiRead.groovy ${dataList[$SLURM_ARRAY_TASK_ID]}


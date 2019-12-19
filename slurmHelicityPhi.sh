#!/bin/bash

#SBATCH --job-name=clasqa_helicityPhi
#SBATCH --account=clas12
#SBATCH --partition=production

#SBATCH --mem-per-cpu=256
#SBATCH --time=1:30:00

#SBATCH --array=1-11
#SBATCH --ntasks=1

#SBATCH --output=/farm_out/%u/%x-%j-%N.out
#SBATCH --error=/farm_out/%u/%x-%j-%N.err

#SBATCH --chdir=$JLAB_SLRUM_O_WORKDIR

dataList=(/cache/clas12/rg-a/production/reconstructed/Fall2018/Torus+1/pass1/v1/*)

srun echo "pwd=$(pwd)"
srun mkdir -p outhipo
srun groovy helicityPhiPlot.groovy ${dataList[$SLURM_ARRAY_TASK_ID]}
srun cp $(ls -t outhipo/*.hipo | head -n1) ${SLURM_SUBMIT_DIR}/outhipo/


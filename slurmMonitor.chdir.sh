#!/bin/bash
# use this version if /work is not mounted on slurm node

#SBATCH --job-name=clasqa_monitor
#SBATCH --account=clas12
#SBATCH --partition=production

#SBATCH --mem-per-cpu=6000
#SBATCH --time=1:30:00

#SBATCH --array=0-10
#SBATCH --ntasks=1

#SBATCH --output=/farm_out/%u/%x-%j-%N.out
#SBATCH --error=/farm_out/%u/%x-%j-%N.err

#SBATCH --chdir=$JLAB_SLRUM_O_WORKDIR

dataList=(/cache/clas12/rg-a/production/reconstructed/Fall2018/Torus+1/pass1/v1/*)

srun echo "pwd=$(pwd)"
srun mkdir -p outhipo
srun groovy monitorRead.groovy ${dataList[$SLURM_ARRAY_TASK_ID]}
srun cp $(ls -t outhipo/*.hipo | head -n1) ${SLURM_SUBMIT_DIR}/outhipo/


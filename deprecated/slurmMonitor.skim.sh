#!/bin/bash

#SBATCH --job-name=clasqa_monitor
#SBATCH --account=clas12
#SBATCH --partition=production

#SBATCH --mem-per-cpu=6000
#SBATCH --time=1:30:00

#SBATCH --array=0-63
#SBATCH --ntasks=1

#SBATCH --output=/farm_out/%u/%x-%j-%N.out
#SBATCH --error=/farm_out/%u/%x-%j-%N.err

dataList=(/work/clas12/rg-a/trains/v16_v2/skim4_inclusive/*)

srun groovy monitorRead.groovy ${dataList[$SLURM_ARRAY_TASK_ID]}

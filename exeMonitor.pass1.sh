#!/bin/bash

datadir="/home/dilks/j/dm/pass1"

slurm=slurm.pass1.dst.bat
> $slurm

function app { echo "$1" >> $slurm; }

nruns=$(ls -d ${datadir}/*/ | wc -l)
let nruns--

app "#!/bin/bash"


app "#SBATCH --job-name=clasqa"
app "#SBATCH --account=clas12"
app "#SBATCH --partition=production"

app "#SBATCH --mem-per-cpu=6000"
app "#SBATCH --time=4:00:00"

app "#SBATCH --array=0-${nruns}"
app "#SBATCH --ntasks=1"

app "#SBATCH --output=/farm_out/%u/%x-%j-%N.out"
app "#SBATCH --error=/farm_out/%u/%x-%j-%N.err"

app "dataList=(${datadir}/00*)"

app "srun groovy monitorRead.groovy \${dataList[\$SLURM_ARRAY_TASK_ID]} dst"

echo "job script"
printf '%70s\n' | tr ' ' -
cat $slurm
printf '%70s\n' | tr ' ' -
echo "submitting to slurm..."
sbatch $slurm
squeue -u `whoami`

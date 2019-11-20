#!/bin/bash
[[ $# -gt 0 ]] && args=$* || args=outhipo/QA*.hipo;
scp $args ${USER}@ftp.jlab.org:/u/group/clas/www/clas12mon/html/hipo/${USER}/

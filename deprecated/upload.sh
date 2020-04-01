#!/bin/bash
[[ $# -gt 0 ]] && args=$* || args=outhipo/QA*.hipo;
scp $args ${USER}@ftp.jlab.org:/path/to/www/dir

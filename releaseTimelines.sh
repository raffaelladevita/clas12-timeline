#!/bin/bash
# copy a locally deployed timeline to the release directory

releaseName="inbending_1"
localName="pass1"

wwwReleaseDir="/group/clas/www/clas12mon/html/hipo/rga/pass1/qa"
wwwLocalDir="/group/clas/www/clas12mon/html/hipo/${USER}"

mkdir -p ${wwwReleaseDir}/${releaseName}
mkdir -p ${wwwReleaseDir}/${releaseName}_extra

cp -v ${wwwLocalDir}/${localName}/* ${wwwReleaseDir}/${releaseName}/
cp -v ${wwwLocalDir}/${localName}_extra/* ${wwwReleaseDir}/${releaseName}_extra/

groovy indexPage.groovy $wwwReleaseDir

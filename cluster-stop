#!/bin/bash

# Use this script to stop all of the cluster nodes started with the start-cluster script.

scriptPath=$(dirname $0)
jarFilename=$(find $scriptPath/target -name *allinone.jar*)

echo "Stop all cluster nodes running on ports 2551 through 2559"
pkill -f $(basename $jarFilename)

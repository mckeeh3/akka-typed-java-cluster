#!/bin/bash

# Use this script to stop one Akka cluster node. The command line parameter must be from 1 to 9.

usage() {
  echo "Usage: $0 node - Stop cluster node, node number must be 1 through 9." ; exit 1
}

stopNode() {
  node=$1
  port="255"$node
  echo "Stop node $1 on port $port"
  pkill -f "$(basename $jarFilename) $port"
}

[ $# -eq 0 ] && usage

node=$1
scriptPath=$(dirname $0)
jarFilename=$(find $scriptPath/target -name *allinone.jar*)

if [[ $node =~ ^[1-9]$ ]] ; then
  stopNode $node
else
  echo "Cluster node number $node is invalid. The node number must be 1 through 9."
  usage
fi

#!/bin/bash

set -e

if [ "$1" != "${1#*[0-9].[0-9]}" ]; then
  ./ipv4 "$1"
elif [ "$1" != "${1#*:[0-9a-fA-F]}" ]; then
  ./ipv6 "$1"
else
  echo "Unrecognized IP format '$1'"
fi
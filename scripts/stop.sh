#!/bin/bash
set -e

CONTAINER_NAME="app7-be"

if [ "$(docker ps -q -f name=$CONTAINER_NAME)" ]; then
  echo "Stopping container: $CONTAINER_NAME"
  docker stop $CONTAINER_NAME
  docker rm $CONTAINER_NAME
  echo "Container stopped."
else
  echo "No running container found: $CONTAINER_NAME"
fi
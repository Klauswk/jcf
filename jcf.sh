#!/bin/bash

pushd $(dirname $0) > /dev/null

java JavaClassFinder.java "$@"

popd > /dev/null

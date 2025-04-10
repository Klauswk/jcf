#!/bin/bash

pushd $(dirname $0) > /dev/null

java jcf.java "$@"

popd > /dev/null

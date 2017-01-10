#!/bin/bash
# Generates all templated files.

set -uexo pipefail

GIT_BASE_DIR=$(git rev-parse --show-toplevel)

cd ${GIT_BASE_DIR}/ServerCore/build
make generated

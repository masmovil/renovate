#!/bin/bash

export GH_VERSION=2.13.0
export OS=linux
export ARCH=amd64

export GH_NAME=gh_${GH_VERSION}_${OS}_${ARCH}
export GH_DOWNLOAD_URL=https://github.com/cli/cli/releases/download/v${GH_VERSION}/${GH_NAME}.tar.gz

wget ${GH_DOWNLOAD_URL} -O ${GH_NAME}.tar.gz
tar xzvf ${GH_NAME}.tar.gz
sudo chmod +x ${GH_NAME}/bin/gh
sudo mv ${GH_NAME}/bin/gh /usr/local/bin/gh
rm -rf ${GH_NAME} ${GH_NAME}.tar.gz

gh version
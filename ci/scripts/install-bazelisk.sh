#!/bin/bash

if ! command -v bazelisk --version &> /dev/null
then
    echo "bazelisk could not be found"
    # Install bazelisk
    wget  https://github.com/bazelbuild/bazelisk/releases/download/v1.7.5/bazelisk-linux-amd64
    chmod +x bazelisk-linux-amd64
    sudo mv -f bazelisk-linux-amd64 /usr/local/bin/bazelisk

    bazelisk --version
fi
echo "✅ bazelisk is installed"
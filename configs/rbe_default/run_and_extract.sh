#!/bin/bash
#
# Copyright 2017 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -ex

# This is a generated file that runs a docker container, waits for it to
# finish running and copies a file to an output location.

DOCKER="/usr/bin/docker"

# Check docker tool is available
if [[ -z "${DOCKER}" ]]; then
    echo >&2 "error: docker not found; do you need to set DOCKER_PATH env var?"
    exit 1
fi

# In system where bind mounting is not supported/allowed, we need to copy the
# scripts and project source code used for Bazel autoconfig to the container.
data_volume=$(docker create -v /rbe_autoconf l.gcr.io/google/rbe-ubuntu16-04@sha256:87e1bb4a47ade8ad4db467a2339bd0081fcf485ec02bcfc3b30309280b38d14b) && /usr/bin/docker cp $(realpath /home/pablo.moncada/.cache/bazel/_bazel_pablo.moncada/563875849e9c3f9dbc00104d207de381/external/bazel_toolchains/rules/cc-sample-project) $data_volume:/rbe_autoconf/project_src && /usr/bin/docker cp /home/pablo.moncada/.cache/bazel/_bazel_pablo.moncada/563875849e9c3f9dbc00104d207de381/external/rbe_default/container $data_volume:/rbe_autoconf/container && /usr/bin/docker cp /home/pablo.moncada/.cache/bazel/_bazel_pablo.moncada/563875849e9c3f9dbc00104d207de381/external/rbe_default/bazelisk $data_volume:/rbe_autoconf/bazelisk

# Pass an empty entrypoint to override any set by default in the container.
id=$(${DOCKER} run -d --entrypoint "" --env ABI_LIBC_VERSION=glibc_2.19 --env ABI_VERSION=clang --env BAZEL_COMPILER=clang --env BAZEL_HOST_SYSTEM=i686-unknown-linux-gnu --env BAZEL_TARGET_CPU=k8 --env BAZEL_TARGET_LIBC=glibc_2.19 --env BAZEL_TARGET_SYSTEM=x86_64-unknown-linux-gnu --env CC=clang --env CC_TOOLCHAIN_NAME=linux_gnu_x86 --env USE_BAZEL_VERSION=2.2.0 --volumes-from $data_volume l.gcr.io/google/rbe-ubuntu16-04@sha256:87e1bb4a47ade8ad4db467a2339bd0081fcf485ec02bcfc3b30309280b38d14b /rbe_autoconf/container/run_in_container.sh)

${DOCKER} wait $id
# Check the docker logs contain the expected 'created outputs_tar' string
if ${DOCKER} logs $id | grep -q 'created outputs_tar'; then
   echo "Successfully created outputs_tar"
else
   echo "Could not create outputs_tar, see docker log for details:"
   echo $(${DOCKER} logs $id)
   exit 1
fi
${DOCKER} cp $id:/rbe_default_out.tar /home/pablo.moncada/.cache/bazel/_bazel_pablo.moncada/563875849e9c3f9dbc00104d207de381/external/rbe_default/output.tar
${DOCKER} rm $id

# If a data volumn is created, delete it at the end.
/usr/bin/docker rm $data_volume

#!/bin/bash

BAZEL_CONFIG=${BAZEL_CONFIG:-remote}

echo "🌀 Bazel build step"
echo "🌀 Bazel Config: \"$BAZEL_CONFIG\""

my_dir="$(dirname "$0")"

source $my_dir/install-bazelisk.sh

if [ "$BAZEL_EXPERIMENTAL_REMOTE_GRPC_LOG" != "" ]; then
    export REMOTE_GRPC_LOG_FILE="$(mktemp)"
    export BAZEL_ARGS="--experimental_remote_grpc_log=$REMOTE_GRPC_LOG_FILE $BAZEL_ARGS"
    echo "Enabling write experimental_remote_grpc_log to $REMOTE_GRPC_LOG_FILE"
fi

export BAZEL_TRACE_PROFILE_FILE="$(mktemp)"
export BAZEL_ARGS="--experimental_generate_json_trace_profile --profile=$BAZEL_TRACE_PROFILE_FILE $BAZEL_ARGS"

gcloud auth configure-docker europe-docker.pkg.dev --quiet
  
bazelisk $BAZEL_STARTUP_OPTIONS run //:push  --verbose_failures --config="$BAZEL_CONFIG" $BAZEL_ARGS

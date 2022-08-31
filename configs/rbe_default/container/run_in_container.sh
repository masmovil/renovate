#!/bin/bash
set -ex
cd .
chmod +x /rbe_autoconf/bazelisk
cd .
cd /rbe_autoconf/project_src
mv BUILD.sample BUILD
touch WORKSPACE
cd /rbe_autoconf/project_src && /rbe_autoconf/bazelisk build @local_config_cc//...
find $(/rbe_autoconf/bazelisk info output_base)/external/local_config_cc -type l -exec bash -c 'ln -f "$(readlink -m "$0")" "$0"' {} \;
mkdir /rbe_autoconf/autoconf_out && cp -dr $(/rbe_autoconf/bazelisk info output_base)/external/local_config_cc /rbe_autoconf/autoconf_out && tar -cf /rbe_default_out.tar -C /rbe_autoconf/autoconf_out/ . 
echo 'created outputs_tar'
cd . ; bazel clean; rm WORKSPACE ; mv BUILD BUILD.sample
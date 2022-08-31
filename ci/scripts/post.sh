#!/bin/bash

echo "🎩 rm -rf /local_ssd/external/npm"
rm -rf /local_ssd/external/npm

echo "🎩 rm -rf /local_ssd/external/postgresql"
rm -rf /local_ssd/external/postgresql

echo "🎩 rm -rf /local_ssd/external/redis"
rm -rf /local_ssd/external/redis

echo "🎩 find /local_ssd/execroot -type d -name \"tmpcharts\" -exec rm -rvf {} +"
find /local_ssd/execroot -type d -name "tmpcharts" -exec rm -rvf {} +
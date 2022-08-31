#!/bin/bash

echo "🌀 Initlializing Jenkins workspace"

echo "🎩 gcloud config set account jenkins-slave@mm-cloudbuild.iam.gserviceaccount.com"
gcloud config set account jenkins-slave@mm-cloudbuild.iam.gserviceaccount.com

echo "🎩 gcloud auth configure-docker europe-docker.pkg.dev --quiet"
gcloud auth configure-docker europe-docker.pkg.dev --quiet
echo "✅ gcloud auth configure-docker europe-docker.pkg.dev --quiet"

echo "🎩 sudo chmod 777 /var/run/docker.sock"
sudo chmod 777 /var/run/docker.sock
echo "✅ sudo chmod 777 /var/run/docker.sock"

echo "🎩 sudo chmod 777 /usr/local/bin/docker"
sudo chmod 777 /usr/local/bin/docker
echo "✅ sudo chmod 777 /usr/local/bin/docker"

echo "🎩 bash ci/scripts/install-bazelisk.sh"
bash ci/scripts/install-bazelisk.sh
echo "✅ bash ci/scripts/install-bazelisk.sh"


echo "🎩 bash ci/scripts/install_gh.sh"
bash ci/scripts/install_gh.sh
echo "✅ bash ci/scripts/install_gh.sh"

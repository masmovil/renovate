
FROM marketplace.gcr.io/google/rbe-ubuntu16-04@sha256:87e1bb4a47ade8ad4db467a2339bd0081fcf485ec02bcfc3b30309280b38d14b AS rbe-ubuntu

# Gcloud sdk
ARG CLOUD_SDK_VERSION=261.0.0
ENV CLOUD_SDK_VERSION=$CLOUD_SDK_VERSION

RUN apt-get update && apt-get install -y apt-transport-https

RUN echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" > /etc/apt/sources.list.d/google-cloud-sdk.list && \
    curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key --keyring /usr/share/keyrings/cloud.google.gpg add - && \
    apt-get update && apt-get install -y google-cloud-sdk kubectl && \
    gcloud config set core/disable_usage_reporting true && \
    gcloud config set component_manager/disable_update_check true && \
    gcloud config set metrics/environment github_docker_image && \
    gcloud auth configure-docker --quiet && \
    gcloud auth configure-docker europe-docker.pkg.dev --quiet && \
    gcloud --version  && kubectl version --client

RUN apt-get install -y iproute2

# Install Helm
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
RUN chmod 700 get_helm.sh
RUN ./get_helm.sh

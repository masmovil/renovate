import groovy.transform.Field

@Field public static final String[] NX_TYPE_OF_JOB = ["lint", "test", "prebuild", "build", "postbuild"]
@Field public static final String[] BUILD_JOBS = ["prebuild", "build", "postbuild"]
@Field String BRANCH_NAME_CACHE_HASH
@Field String CACHE_DEPS_HASH
@Field String GIT_LOG_LAST
@Field String GIT_COMMIT_AUTHOR
@Field Map<String, String> publishURLs = [:]
@Field String REGEX_PROD = '^v[0-9]+(\\.[0-9]+)*$'
@Field String REGEX_STAGING = '^v[0-9]+(\\.[0-9]+)*-rc(\\.([0-9])+)?$'
@Field String PUBLISH_BASE_URL_DEV = 'private.dev-01.k8s.masmovil.com'
@Field String PUBLISH_BASE_URL_PROD = 'private.prod-01.k8s.masmovil.com'
@Field String PUBLISH_BASE_URL_BRANCH = 'private.dev-02.k8s.masmovil.com'

//  a88888b.  .88888.  888888ba  d888888P  88888888b dP    dP d888888P
// d8'   `88 d8'   `8b 88    `8b    88     88        Y8.  .8P    88
// 88        88     88 88     88    88    a88aaaa     Y8aa8P     88
// 88        88     88 88     88    88     88        d8'  `8b    88
// Y8.   .88 Y8.   .8P 88     88    88     88        88    88    88
//  Y88888P'  `8888P'  dP     dP    dP     88888888P dP    dP    dP

public getContextStash() {
  sh(label: "💻 JENKINS NODE NAME", script: "printenv | grep NODE_NAME")
  echo "⏬ GET CONTEXT STASH"
  unstash 'context'
  sh '''#!/bin/bash
    source ./context/env.sh
  '''
  sh "printenv | sort"
}

public setContextStash() {
  echo "💾 SET CONTEXT STASH"
  checkout scm
  sh """#!/bin/bash
    set -a
    env
    mkdir -p ./context ./deployment
    gsutil -m cp -r "gs://cloudbuild-contexts/${CONTEXT}/*" ./context
    gsutil -m cp -r 'gs://continuous-deployment-scripts/deployment/*' ./deployment
    chmod +x ./deployment/*.sh
    source ./deployment/env.sh
    source ./deployment/deployment-helper.sh

    read -r K8S_CLUSTER_NAME K8S_NAMESPACE K8S_VERSION < <(get_cluster_env)
    echo ${K8S_CLUSTER_NAME} > K8S_CLUSTER_NAME
    echo ${K8S_NAMESPACE} > K8S_NAMESPACE

    if [[ "${GIT_BRANCH}" =~ ${REGEX_PROD} ]]; then
      echo "prod" > ENV
      echo "${PUBLISH_BASE_URL_PROD}" > PUBLISH_BASE_URL
    elif [[ "${GIT_BRANCH}" =~ ${REGEX_STAGING} ]]; then
      echo "staging" > ENV
      echo "${PUBLISH_BASE_URL_DEV}" > PUBLISH_BASE_URL
    else
      echo "dev" > ENV
      echo "${PUBLISH_BASE_URL_BRANCH}" > PUBLISH_BASE_URL
    fi

    echo ENV = \$(cat ENV)
    echo PUBLISH_BASE_URL = \$(cat PUBLISH_BASE_URL)
    echo K8S_CLUSTER_NAME = \$(cat K8S_CLUSTER_NAME)
    echo K8S_NAMESPACE = \$(cat K8S_NAMESPACE)
  """
  stash includes: 'deployment/,context/', name: 'context'
  script {
      ENV=readFile('ENV').trim()
      PUBLISH_BASE_URL=readFile('PUBLISH_BASE_URL').trim()
      K8S_CLUSTER_NAME=readFile('K8S_CLUSTER_NAME').trim()
      K8S_NAMESPACE=readFile('K8S_NAMESPACE').trim()
  }
  sh 'node -v'
  sh "printenv | sort"
}

// 888888ba   88888888b  888888ba  .d88888b
// 88    `8b  88         88    `8b 88.    "'
// 88     88 a88aaaa    a88aaaa8P' `Y88888b.
// 88     88  88         88              `8b
// 88    .8P  88         88        d8'   .8P
// 8888888P   88888888P  dP         Y88888P

public genDepsCacheHash() {
  CACHE_DEPS_HASH = sh(returnStdout: true, script: "md5sum package-lock.json | awk '{print \$1}' | cut -d\\  -f1 | tr -d '\n' ")
  echo "CACHE_DEPS_HASH = '${CACHE_DEPS_HASH}'"
}

public getDepsCache(clean=true, skipInstall=false) {
  echo "⏬ GET DEPS CACHE OR INSTALL"
  githubCredentials {
    sh """#!/bin/bash
      set +x
      set -ea

      STEP=0
      CACHEFILE="deps.${CACHE_DEPS_HASH}.tar.gz"

      # [STEP 1]
      echo "🔸 [STEP \$((STEP=STEP+1))] Clean"
      rm -Rf node_modules

      # [STEP 2]
      echo "🔸 [STEP \$((STEP=STEP+1))] Config GCloud"
      export PATH=\$PATH:/snap/bin
      gcloud config list account --format "value(core.account)"

      # [STEP 3]
      echo "🔸 [STEP \$((STEP=STEP+1))] Get Cache"
      echo "gs://${CACHE_BUCKET}/\$CACHEFILE"
      found=\$(gsutil stat gs://${CACHE_BUCKET}/\$CACHEFILE || echo 1)
      if [[ \$found != 1 ]] ; then
        echo "✅ Cache Match"
        if [[ ${skipInstall} == true ]] ; then
          exit 0
        fi
        gsutil -q cp gs://${CACHE_BUCKET}/\$CACHEFILE \$CACHEFILE
        tar -xzf \$CACHEFILE
        if \$clean ; then
          echo "🗑 Clean CACHEFILE"
          rm -f \$CACHEFILE
        fi
      else
        echo "🔄 No cache match -> yarn install"
        #source ./context/env.sh
        npm set //npm.pkg.github.com/:_authToken=\$GITHUB_PACKAGES_ACCESS_TOKEN
        npm ci
        pwd
        ls -la
      fi

      set -x
    """
  }
}

public setDepsCache() {
  //? This fn has to be outside the sshagent-withCredentials
  //? combo, as that user doesn't have the bucket upload
  //? permisions needed
  echo "💾 SET DEPS CACHE"
  sh """#!/bin/bash
    CACHEFILE="deps.${CACHE_DEPS_HASH}.tar.gz"

    found=\$(gsutil stat gs://${CACHE_BUCKET}/\$CACHEFILE || echo 1)
    if [[ \$found == 1 ]] ; then
      echo "💾 Save cache: \$CACHEFILE"
      tar czf \$CACHEFILE node_modules
      gsutil -q cp \$CACHEFILE gs://\$CACHE_BUCKET/

      echo "🧼 Clean after save"
      rm -f \$CACHEFILE
    else
      echo "Skip save, as it's already in the bucket..."
    fi
  """
}

//  888888ba  dP     dP dP dP        888888ba
//  88    `8b 88     88 88 88        88    `8b
// a88aaaa8P' 88     88 88 88        88     88
//  88   `8b. 88     88 88 88        88     88
//  88    .88 Y8.   .8P 88 88        88    .8P
//  88888888P `Y88888P' dP 88888888P 8888888P

public genBranchNameCacheHash() {
  BRANCH_NAME_CACHE_HASH = env.BRANCH_NAME == 'master' ? 'master' : sh(returnStdout: true, script: "printf '%s' '${BRANCH_NAME}' | md5sum | awk '{print \$1}' | tr -d '\n' ")
  echo "BRANCH_NAME_CACHE_HASH = '${BRANCH_NAME_CACHE_HASH}'"
}

public getBuildCache(pkgName, Boolean skipClean = false) {
  echo "⏬ GET BUILD CACHE"
  if (!pkgName) {
    echo "No pkgName?!"
    exit 1
  }
  githubCredentials {
    sh """#!/bin/bash
      set +x
      set -ea
      STEP=0
      CACHEFILE="build.${BRANCH_NAME_CACHE_HASH}-${pkgName}.tar.gz"

      # [STEP 1]
      echo "🔸 [STEP \$((STEP=STEP+1))] Config GCloud"
      export PATH=\$PATH:/snap/bin
      gcloud config list account --format "value(core.account)"

      # [STEP 2]
      echo "🔸 [STEP \$((STEP=STEP+1))] Get Cache"
      echo "gs://${CACHE_BUCKET}/\$CACHEFILE"
      found=\$(gsutil stat gs://${CACHE_BUCKET}/\$CACHEFILE || echo 1)
      if [[ \$found != 1 ]] ; then
        echo "✅ Cache Match"
        gsutil -q cp gs://${CACHE_BUCKET}/\$CACHEFILE \$CACHEFILE
        mkdir -p build
        tar -xzf \$CACHEFILE -C build
        rm -f \$CACHEFILE
      else
        echo "restoreBuild(): NO existe build cacheada :("
        exit 1
      fi
      set -x
    """
  }
}

public setBuildCache(pkgName) {
  //? This fn has to be outside the sshagent-withCredentials
  //? combo, as that user doesn't have the bucket upload
  //? permisions needed
  echo "💾 SET BUILD CACHE"
  if (!pkgName) {
    echo "No pkgName?!"
    exit 1
  }
  sh """#!/bin/bash
    CACHEFILE="build.${BRANCH_NAME_CACHE_HASH}-${pkgName}.tar.gz"

    echo "💾 Save build"
    export PATH=\$PATH:/snap/bin

    tar czf \$CACHEFILE -C ./dist/apps/${pkgName}/ .
    gsutil -q cp \$CACHEFILE gs://${CACHE_BUCKET}/
    # //TODO: Is this necesary?
    sleep 10 # Wait until file is already consistent in cache

    echo "🧼 Clean after save"
    rm -f \$CACHEFILE
  """
}

public cleanAll() {
  //? Anything we identify as "trash" after build should be added
  sh '''
    rm -f *.tar.gz || true
    rm -Rf dist build node_modules .cache tmp out-tsc || true
  '''
}

// 888888ba   88888888b  888888ba  dP         .88888.  dP    dP
// 88    `8b  88         88    `8b 88        d8'   `8b Y8.  .8P
// 88     88 a88aaaa    a88aaaa8P' 88        88     88  Y8aa8P
// 88     88  88         88        88        88     88    88
// 88    .8P  88         88        88        Y8.   .8P    88
// 8888888P   88888888P  dP        88888888P  `8888P'     dP
public buildDockerImage(IMAGE_TAG=null) {
  checkout scm
  getContextStash()
  sh "printenv | sort"
  getDepsCache()
  getBuildCache(NX_PROJECT_NAME)
  env.DOCKER_IMAGE_TAG = IMAGE_TAG
  sh """#!/bin/bash +x
    echo "0️⃣ prepare..."
    # This is temporary for PATH fixing
    export PATH=\$PATH:/snap/bin
    source ./context/env.sh
    source ./deployment/env.sh
    set -x

    export DOCKER_IMAGE_TAG=${env.DOCKER_IMAGE_TAG}
    echo "***🔸🔸🔸 \$DOCKER_IMAGE_TAG"
    printenv | sort

    echo "1️⃣ build-image"
    ./deployment/build-image.sh ./apps/${NX_PROJECT_NAME}/Dockerfile

    echo "2️⃣ push-docker-image"
    ./deployment/push-docker-image.sh
  """
}

public buildDockerDepsImage(NX_PROJECT_NAME) {
  echo "👷🏿 buildDockerDepsImage"
  sh """#!/bin/bash +x
    echo "0️⃣ prepare..."
    # This is temporary for PATH fixing
    export PATH=\$PATH:/snap/bin
    source ./context/env.sh
    source ./deployment/env.sh
    source ./deployment/deployment-helper.sh
    set -x

    export DOCKER_IMAGE_TAG="deps-${CACHE_DEPS_HASH}"
    echo "***🔸🔸🔸 \$DOCKER_IMAGE_TAG"

    gcloud_auth # This is needed for gcloud to work!
    gcloud auth configure-docker --quiet
    found=\$(gcloud container images list-tags --filter="\$DOCKER_IMAGE_TAG" --format=json eu.gcr.io/mm-cloudbuild/pepephone/${NX_PROJECT_NAME} | jq length)
    if [[ \$found != 0 ]] ; then
      echo "✅ Image Match: we don't need to build Dockerfile.dev"
    else
      echo "1️⃣ build-image"
      ./deployment/build-image.sh ./apps/${NX_PROJECT_NAME}/Dockerfile.deps

      echo "2️⃣ push-docker-image"
      # CUSTOM! from ./deployment/push-docker-image.sh
      docker tag "${KUBERNETES_SERVICE_NAME}:\$DOCKER_IMAGE_TAG" "\$DOCKER_REGISTRY_URL/\$KUBERNETES_SERVICE_NAME:\$DOCKER_IMAGE_TAG"
      docker push "\$DOCKER_REGISTRY_URL/\$KUBERNETES_SERVICE_NAME:\$DOCKER_IMAGE_TAG"
    fi
  """
}

public buildDockerBuildImage(NX_PROJECT_NAME) {
  echo "👷🏻‍♀️ buildDockerDepsImage"
  sh """#!/bin/bash +x
    echo "0️⃣ prepare..."
    # This is temporary for PATH fixing
    export PATH=\$PATH:/snap/bin
    source ./context/env.sh
    source ./deployment/env.sh
    set -x

    echo "1️⃣ FROM deps-image"
    sed -ie '1s|.*|FROM '\$DOCKER_REGISTRY_URL'/${KUBERNETES_SERVICE_NAME}:deps-${CACHE_DEPS_HASH}|' ./apps/${NX_PROJECT_NAME}/Dockerfile

    cat ./apps/${NX_PROJECT_NAME}/Dockerfile
    # ignore node_modules from beign copied to the image,
    # as we'll use the deps-image as base
    echo "**/node_modules" >> .dockerignore
    echo "node_modules" >> ./apps/${NX_PROJECT_NAME}/.dockerignore

    echo "2️⃣ build-image"
    ./deployment/build-image.sh ./apps/${NX_PROJECT_NAME}/Dockerfile

    echo "3️⃣ push-docker-image"
    ./deployment/push-docker-image.sh
  """
}



public deployToK8n() {
  getContextStash()
  installYQ()
  sh "printenv | sort"
  sh '''#!/bin/bash +x
      set +x
      set -ea

      yq --version

      # Enable for Branches without TAG.
      yq w -i \$HELM_CHART_FOLDER_NAME/values.yaml base.istio.virtualservice.enabled "true"

      source ./scripts/deploy-2-k8s.sh
      set -x
    '''
  def URL = readFile('PUBLISH_URL').trim()
  echo "DEPLOYED AT: ${URL}"
  return URL
}

public chartRelease(String IMAGE_TAG, Boolean UPDATE_IMAGE = true){
  echo "Chart Museum ${NX_PROJECT_NAME}"
  checkout scm
  getContextStash()
  installYQ()
  env.DOCKER_IMAGE_TAG = IMAGE_TAG
  env.DOCKER_UPDATE_IMAGE = UPDATE_IMAGE
  sh """#!/bin/bash +x
    source ./context/env.sh
    source ./deployment/env.sh
    source ./deployment/deployment-helper.sh
    set -x

    setup_k8s_config "\$CLUSTER_NAME"
    HELM_UPDATE_IMAGE=${env.DOCKER_UPDATE_IMAGE}

    export DOCKER_IMAGE_TAG=${env.DOCKER_IMAGE_TAG}
    echo "***🔸🔸🔸 \$DOCKER_IMAGE_TAG"
    printenv | sort

    #####helm_release
    set -e
    echo "⎈  Helm: Initializing helm"
    helm_init

    echo "⎈  Helm: Packaging helm"
    helm_package

    echo "⎈  Helm: Pushing changes"
    set +e

    for HELM_PACKAGE in "\$HELM_CHARTS_TMP_PATH"/*.tgz ; do
      echo "Helm cm-push package \$HELM_PACKAGE"

      OUTPUT=\$(\$HELM_BIN cm-push "\$HELM_PACKAGE" "\$HELM_REPO_NAME")
      NPKG=\$(echo "\$HELM_PACKAGE" | rev | cut -d/ -f1 | rev)

      if [[ \$OUTPUT == *"Error: 409"* ]]; then
        echo \$HELM_PACKAGE | awk -F: '{print \$NF}'
        echo "Ya estaba subido en el chartmuseum \$NPKG"
      else
        echo "--> \$OUTPUT"
      fi
    done

    printenv | sort
  """
}

public installYQ() {
  sh '''#!/bin/bash +x
    platform='unknown'
    unamestr="$(uname)"

    if [[ "$unamestr" == 'Linux' ]]; then
      platform='linux'
    elif [[ "$unamestr" == 'Darwin' ]]; then
      platform='darwin'
    fi

    YAML="/tmp/yq"
    YQ="/tmp/yq"

    if ! [ -x "$(command -v yq)" ]; then
    echo "yaml is not installed: Downloading..."
      wget -O "$YAML" https://github.com/mikefarah/yq/releases/download/1.13.1/yaml_${platform}_amd64
      sudo chmod +x "$YAML"
      sudo cp "$YAML" /usr/local/bin/
    else
      YQ=$(command -v yq)
      YAML=$(command -v yq)
    fi

    export YAML YQ
  '''
}

public installHelm() {
  sh '''#!/bin/bash +x
    HELM_VERSION="3.8.2"
    HELM_BIN="/usr/local/bin/helm"

    if ! [ -x "$(command -v helm)" ]; then
      echo "helm is not installed: Downloading..."
      TMP_FOLDER=$(mktemp -d) ; cd "$TMP_FOLDER"
      curl -L "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz" -o helm.tgz
      tar xvzf helm.tgz
      sudo mv linux-amd64/helm "$HELM_BIN"
      sudo chown root:root "$HELM_BIN" && sudo chmod +x "$HELM_BIN"
    fi
  '''
}

// 888888ba  dP    dP  a88888b.  .d888888   a88888b. dP     dP   88888888b
// 88    `8b Y8.  .8P d8'   `88 d8'    88  d8'   `88 88     88   88
// 88     88  Y8aa8P  88        88aaaaa88a 88        88aaaaa88a a88aaaa
// 88     88 d8'  `8b 88        88     88  88        88     88   88
// 88     88 88    88 Y8.   .88 88     88  Y8.   .88 88     88   88
// dP     dP dP    dP  Y88888P' 88     88   Y88888P' dP     dP   88888888P

public genAffectedPKGJobWithcache(String job) {
  withEnv([
    "BRANCH_NAME_CACHE_HASH=${BRANCH_NAME_CACHE_HASH}",
    "CACHE_DEPS_HASH=${CACHE_DEPS_HASH}",
    "CONTEXT=${CONTEXT}",
    "ENV=${ENV}",
    "K8S_CLUSTER_NAME=${K8S_CLUSTER_NAME}",
    "K8S_NAMESPACE=${K8S_NAMESPACE}",
    "PUBLISH_BASE_URL=${PUBLISH_BASE_URL}",
  ]) {
    stage("⚙️ ${job}") {
      newNode('node-lts') {
        gitNotify("Frying ${job}", "Pipeline for ${job} started",  'PENDING')
        cleanAll()
        checkout scm
        getContextStash()
        getDepsCache()
        getNXJobCache(job)
        shared.githubCredentials {
          sh 'git fetch https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/masmovil/pepe-nx.git master:master --force --quiet'
        }
        def affectedPKGs = sh(returnStdout: true, script: "./node_modules/.bin/nx print-affected --target ${job} | jq '.tasks[].target.project' |  xargs | sed -e 's/ /, /g'")
        currentBuild.description = "${currentBuild.description}<p>  - <b>⚙️ ${job} with cache</b>: ${affectedPKGs}</p>"
        def String baseSha = (env.CHANGE_ID || env.BRANCH_NAME != 'master') ? 'master' : 'master~1'
        sh "./node_modules/.bin/nx affected:${job} --base=${baseSha} --parallel=\$(expr \$(nproc --all) - 1)"
        setNXJobCache(job)
        gitNotify("Frying ${job}", "Pipeline for ${job} started",  'SUCCESS')
      }
    }
  }
}

public getNXJobCache(String job) {
  echo "⏬ GET NX ${job.toUpperCase()} CACHE"
  githubCredentials {
    sh """#!/bin/bash
      set +x
      set -ea
      STEP=0
      CURRENT_CACHE_FILE="nxcache.${job.toLowerCase()}.${BRANCH_NAME_CACHE_HASH}.tar.gz"
      MASTER_CACHE_FILE="nxcache.${job.toLowerCase()}.master.tar.gz"

      # [STEP 1]
      echo "🔸 [STEP \$((STEP=STEP+1))] Config GCloud"
      export PATH=\$PATH:/snap/bin
      gcloud config list account --format "value(core.account)"

      # [STEP 2]
      echo "🔸 [STEP \$((STEP=STEP+1))] Get Cache"
      echo "gs://${CACHE_BUCKET}/\$CURRENT_CACHE_FILE"
      brachCacheFound=\$(gsutil stat gs://${CACHE_BUCKET}/\$CURRENT_CACHE_FILE || echo 1)
      if [[ \$GIT_BRANCH == "master" || \$brachCacheFound == 1 ]] ; then
        echo "gs://${CACHE_BUCKET}/\$MASTER_CACHE_FILE"
        masterCacheFound=\$(gsutil stat gs://${CACHE_BUCKET}/\$MASTER_CACHE_FILE || echo 1)
        if [[ \$masterCacheFound != 1 ]] ; then
          echo "Master Cache Match ✅ "
          gsutil -q cp gs://${CACHE_BUCKET}/\$MASTER_CACHE_FILE \$MASTER_CACHE_FILE
          tar -xzf \$MASTER_CACHE_FILE
          rm -f \$MASTER_CACHE_FILE
        else
          echo "WARN | getNXJobCache(): No existe cache de ${job} para master?!"
        fi
      else
        echo "Branch Cache Match ✅ "
        gsutil -q cp gs://${CACHE_BUCKET}/\$CURRENT_CACHE_FILE \$CURRENT_CACHE_FILE
        tar -xzf \$CURRENT_CACHE_FILE
        rm -f \$CURRENT_CACHE_FILE
      fi
      set -x
    """
  }
}

public setNXJobCache(String job) {
  //? This fn has to be outside the sshagent-withCredentials
  //? combo, as that user doesn't have the bucket permisions needed
  //? to upload the cache
  echo "💾 SET NX ${job.toUpperCase()} CACHE"
  sh """#!/bin/bash
    CACHEFILE="nxcache.${job.toLowerCase()}.${BRANCH_NAME_CACHE_HASH}.tar.gz"
    if [[ \$GIT_BRANCH == "master" ]] ; then
      CACHEFILE="nxcache.${job.toLowerCase()}.master.tar.gz"
    fi
    echo "gs://${CACHE_BUCKET}/\$CACHEFILE"

    echo "💾 Save NX cache"
    export PATH=\$PATH:/snap/bin
    tar czf \$CACHEFILE .cache
    gsutil rm -f -a gs://${CACHE_BUCKET}/\$CACHEFILE
    gsutil -q cp \$CACHEFILE gs://${CACHE_BUCKET}/
    sleep 10 # Wait until file is already consistent in cache

    echo "🧼 Clean after save"
    rm -f \$CACHEFILE
  """
}

public getNXBuildCache(String project) {
  echo "⏬ GET NX ${project} BUILD CACHE"
  githubCredentials {
    sh """#!/bin/bash
      set +x
      set -ea
      STEP=0
      CURRENT_CACHE_FILE="nxcache.${project}.build.${BRANCH_NAME_CACHE_HASH}.tar.gz"
      MASTER_CACHE_FILE="nxcache.${project}.build.master.tar.gz"

      # [STEP 1]
      echo "🔸 [STEP \$((STEP=STEP+1))] Config GCloud"
      export PATH=\$PATH:/snap/bin
      gcloud config list account --format "value(core.account)"

      # [STEP 2]
      echo "🔸 [STEP \$((STEP=STEP+1))] Get Cache"
      echo "gs://${CACHE_BUCKET}/\$CURRENT_CACHE_FILE"
      brachCacheFound=\$(gsutil stat gs://${CACHE_BUCKET}/\$CURRENT_CACHE_FILE || echo 1)
      if [[ \$GIT_BRANCH == "master" || \$brachCacheFound == 1 ]] ; then
        echo "gs://${CACHE_BUCKET}/\$MASTER_CACHE_FILE"
        masterCacheFound=\$(gsutil stat gs://${CACHE_BUCKET}/\$MASTER_CACHE_FILE || echo 1)
        if [[ \$masterCacheFound != 1 ]] ; then
          echo "Master Cache Match ✅ "
          gsutil -q cp gs://${CACHE_BUCKET}/\$MASTER_CACHE_FILE \$MASTER_CACHE_FILE
          tar -xzf \$MASTER_CACHE_FILE
          rm -f \$MASTER_CACHE_FILE
        else
          echo "WARN | getNXJobCache(): No existe cache de build de ${BRANCH_NAME_CACHE_HASH} para master?!"
        fi
      else
        echo "Branch Cache Match ✅ "
        gsutil -q cp gs://${CACHE_BUCKET}/\$CURRENT_CACHE_FILE \$CURRENT_CACHE_FILE
        tar -xzf \$CURRENT_CACHE_FILE
      fi
      set -x
    """
  }
}

@NonCPS
public isBuildSkiped(String buildResult) {
  def strings = [
    'output from the cache',
    'retrieved from cache',
    'local cache'
  ]
  Boolean strFound = strings.any{buildResult.contains(it) }

  return strFound
}


public setNXBuildCache(String project) {
  //? This fn has to be outside the sshagent-withCredentials
  //? combo, as that user doesn't have the bucket upload
  //? permisions needed
  echo "💾 SET NX ${project} BUILD CACHE"
  sh """#!/bin/bash
    CACHEFILE="nxcache.${project}.build.${BRANCH_NAME_CACHE_HASH}.tar.gz"
    if [[ \$GIT_BRANCH == "master" ]] ; then
      CACHEFILE="nxcache.${project}.build.master.tar.gz"
    fi
    echo "gs://${CACHE_BUCKET}/\$CACHEFILE"

    echo "💾 Save NX cache"
    export PATH=\$PATH:/snap/bin
    tar czf \$CACHEFILE .cache
    gsutil rm -f -a gs://${CACHE_BUCKET}/\$CACHEFILE
    gsutil -q cp \$CACHEFILE gs://${CACHE_BUCKET}/
    sleep 10 # Wait until file is already consistent in cache

    echo "🧼 Clean after save"
    rm -f \$CACHEFILE
  """
}

public getNXCacheJobStash(String job) {
  echo "⏬ UNSTASH NX CACHE FOR ${job.toUpperCase()}"
  unstash "nxCache-${job}"
}

public setNXCacheJobStash(String job) {
  echo "💾 STASH NX CACHE FOR ${job.toUpperCase()}"
  stash includes: '.cache/', name: "nxCache-${job}"
}


//? dP     dP   88888888b dP         888888ba   88888888b  888888ba  .d88888b
//? 88     88   88        88         88    `8b  88         88    `8b 88.    "'
//? 88aaaaa88a a88aaaa    88        a88aaaa8P' a88aaaa    a88aaaa8P' `Y88888b.
//? 88     88   88        88         88         88         88   `8b.       `8b
//? 88     88   88        88         88         88         88     88 d8'   .8P
//? dP     dP   88888888P 88888888P  dP         88888888P  dP     dP  Y88888P

public newNode(String type, Closure cl) {
  //? Wrapper that creates a new node
  node('pepephone') {
    docker.withRegistry('https://europe-docker.pkg.dev', 'gcr:jenkins-slave@mm-cloudbuild') {
      switch(type) {
        case 'helm':
          docker.image('mm-platform-sre-prod/container-images-private/ci/helm:stable').inside('--tmpfs /.config', cl)
          break;
        case 'node':
          docker.image('mm-platform-sre-prod/container-images-private/ci/node:master').inside('--tmpfs /.config', cl)
          break;
        case 'node-lts':
          docker.image('mm-platform-sre-prod/container-images-private/ci/node-lts:latest').inside('--tmpfs /.config', cl)
          break;
        break
          docker.image('mm-platform-sre-prod/container-images-private/ci/base:master').inside('--tmpfs /.config', cl)
          break;
      }
    }
  }
}

public githubCredentials(Closure cl) {
  //? Wrapper that sets a sshagent-withCredentials combo
  sshagent (credentials: ['github-ssh']) {
    withCredentials(
      [
        usernamePassword(
          credentialsId: '806bdc4e-af90-4255-83fc-b434c30a6720',
          passwordVariable: 'GIT_PASSWORD',
          usernameVariable: 'GIT_USERNAME'
        )
      ]
    ) {
      cl.call()
    }
  }
}


public retry(final Closure cl, int maxAttempts, int timeoutSeconds, final int count = 1) {
  echo "❤️‍🔥 Try #${count}"
  try {
    cl.call();
  } catch (final exception) {
    echo "${exception.toString()}"
    timeout(time: timeoutSeconds, unit: 'SECONDS') {
      if (count <= maxAttempts) {
        echo "❤️‍🩹 Retrying..."
        return retry(cl, maxAttempts, timeoutSeconds, count + 1)
      } else {
        echo "💔 Max attempts reached: Will not retry anymore. (._.)"
        throw exception
      }
    }
  }
}

def getPropOrDefault( Closure cl, def defaultVal ) {
    try {
        return cl()
    }
    catch( groovy.lang.MissingPropertyException e ) {
        return defaultVal
    }
}

public gitNotify(String context, String description, String status) {
  try {
      githubNotify context: context, description: description,  status: status
  } catch(err) {
      echo err
  }
}

public genLastCommitMsg() {
  GIT_LOG_LAST = sh(returnStdout: true, script: "git log -1 --pretty=%B | tr -d '\n' ")
  echo "GIT_LOG_LAST = '${GIT_LOG_LAST}'"
}

public genGitCommitUser() {
  GIT_COMMIT_AUTHOR = sh(returnStdout: true, script: "git show -s --format='%ae' ${GIT_COMMIT} | grep '' | tr -d '\n' ")
  echo "GIT_COMMIT_AUTHOR = '${GIT_COMMIT_AUTHOR}'"
}

@NonCPS
public setPublisedUrl(String NX_PROJECT_NAME, String url) {
  publishURLs[NX_PROJECT_NAME] = url
}

@NonCPS
public getGHPublisedUrls() {
  String GH_MSG = ""
  publishURLs.each { pkg, url ->
    GH_MSG = GH_MSG + "- ${pkg}: ${url}"
    if (pkg != publishURLs.keySet()[-1]) {
      GH_MSG = GH_MSG + "\n"
    }
  }
  return GH_MSG
}

public getGChatCommitInfo() {
  String author = getPropOrDefault({ CHANGE_AUTHOR_DISPLAY_NAME }, GIT_COMMIT_AUTHOR)
  String commit = getPropOrDefault({ GIT_LOG_LAST }, '')
  String GCHAT_MSG = "<b>Author</b>: $author<br>"
  GCHAT_MSG = GCHAT_MSG + "<b>GCMSG</b>: $commit"
  return GCHAT_MSG
}

public getGChatMention() {
  switch(GIT_COMMIT_AUTHOR) {
    case 'alvaro.almendros@masmovil.com':
      return '104474346792979503200'
    case 'jesus.vallez@masmovil.com':
    case 'jesusvg91@gmail.com':
      return '114854054913428547505'
    case 'maria.moreno@masmovil.com':
    case 'mariolamoreno@hotmail.es':
      return '112730113493298963354'
    case 'tomas.calvo@masmovil.com':
    case 'calvo.tom@gmail.com':
      return '112320508150832766713'
  }
}

public getGChatPublisedUrls() {
  String GCHAT_MSG = ""
  if (publishURLs.size() > 0) {
    GCHAT_MSG = 'Desplegado en:<ul>'
    publishURLs.each { pkg, url ->
      GCHAT_MSG = GCHAT_MSG + "<li>🚀  <a href=\"$url\">$pkg</a></li>"
    }
    GCHAT_MSG = GCHAT_MSG + '</ul>'
  } else {
    GCHAT_MSG = 'No url to report'
  }
  return GCHAT_MSG
}

public getGChatErrorInfo(String pkg, String err) {
  String GCHAT_MSG = "<b>PKG</b>: $pkg<br>"
  GCHAT_MSG = GCHAT_MSG + "$err"
  return GCHAT_MSG
}

public githubNotifyURLs() {
  githubCredentials {
    if (BRANCH_NAME != "master") {
      pullRequest.comment("Desplegado en:\n${getGHPublisedUrls()}")
    }
  }
}

public setBuildDescriptionURLs() {
  if (BRANCH_NAME != "master") {
    String DESC_MSG = ""
    publishURLs.each { pkg, url ->
      DESC_MSG = DESC_MSG + "<li><b>${pkg}</b>: <a href=\"${url}\">${url}</a></li>"
    }
    currentBuild.description = "${currentBuild.description}<br><h4>🚀 Desplegado en:</h4><ul>${DESC_MSG}</ul>"
  }
}

public purgeFastly() {
  sh 'curl -X POST -H Fastly-Key:$FASTLY_API_TOKEN https://api.fastly.com/service/$FASTLY_FRONTEND_SERVICE_ID/purge_all'
}

public gchatBotCardTag(String customMessage, String title = 'Deploy', String color = 'default') {
  echo "gchatBotCardTag - title: ${title}"
  echo "gchatBotCardTag - color: ${color}"
  echo "gchatBotCardTag - customMessage: ${customMessage}"
  String specialCharRegex = /[']/
  String msg = customMessage.replaceAll(specialCharRegex, '') //remove especial chars

  String branch = getPropOrDefault({ CHANGE_BRANCH }, GIT_BRANCH)
  String ghurl = getPropOrDefault({ CHANGE_URL }, GIT_URL)

  String mention = getGChatMention()

  String img = 'https://i.imgur.com/kp2FXCF.png'
  switch (color) {
    case 'success':
      img = 'https://i.imgur.com/kMaPdTY.png'
      break
    case 'start':
      img = 'https://i.imgur.com/8zN7qL6.png'
      break
    case 'error':
      img = 'https://i.imgur.com/MyPE2Ff.png'
      break
  }

  sh """
    curl --location --request POST 'https://chat.googleapis.com/v1/spaces/AAAAY1UcoKQ/messages?key=AIzaSyDdI0hCZtE6vySjMm-WEfRq3CPzqKqqsHI&token=LKCKyMZ0UjMsQ8mWfjk_FsGwcTB92rtjykL2V8Fklkc%3D&threadKey=${GIT_COMMIT}' \
      -H 'Content-Type: text/plain' \
      --data-raw "{
          'cards': [
            {
             'header': {
               'title': '${title}',
               'subtitle': '${branch}',
               'imageUrl': '${img}'
             },
             'sections': [
              {
               'header': 'Cambios:',
               'widgets': [
                 {
                  'textParagraph': {
                   'text': '${msg}'
                  }
                 }
               ]
              },
              {
               'widgets': [
                {
                 'buttons': [
                  {
                   'textButton': {
                    'text': 'Ver en Jenkins',
                    'onClick': {
                     'openLink': {
                      'url': '${RUN_DISPLAY_URL}'
                     },
                    },
                   },
                  },
                  {
                   'textButton': {
                    'text': 'Abrir en github',
                    'onClick': {
                     'openLink': {
                      'url': '${ghurl}'
                     },
                    },
                   },
                  },
                 ],
                },
               ]
              },
             ]
            },
          ],
          text: '<users/${mention}>, ${title}: ${branch}'
         }"
  """
}

def getTotalCommitsForBuild(build) {
  def changeSet = build.changeSets
  def commits = 0
  for (int i = 0; i < changeSet.size(); i++) {
    commits += changeSet[i].items.size()
  }
  return commits
}

// ├┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┤
// ├┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┤
// ├┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┤
// ├┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┤
// ├┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┬┴┤

return this;

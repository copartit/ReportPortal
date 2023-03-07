#!/usr/bin/env groovy

// clean install package docker:build -DpushImage -DcreateChecksum=true sonar:sonar
def msg =''
def app_name = ''
def app_workspace = "." // default workdir
def app_pom_path = ""
def country =""
def app_version=""
def commitid=""
def short_sha=""
def DEPLOY=true
def STACK=null
def TRACK=null
def deploymentJson=""
def IMAGE_TAG = null

def notifySlack(String buildStatus="STARTED", String customMsg="", String app_name="") {
    // Build status of null means success.
    buildStatus = buildStatus ?: 'SUCCESS'
    def color

    if (buildStatus == 'STARTED') {
        color = '#D4DADF'
    } else if (buildStatus == 'SUCCESS') {
        color = '#BDFFC3'
    } else if (buildStatus == 'UNSTABLE') {
        color = '#FFFE89'
    } else {
        color = '#FF9FA1'
    }

    def msg = "Repository: ${env.GIT_URL}\nBranch : ${env.GIT_BRANCH}\nApp Name: ${app_name} \nAuthor : ${env.CHANGE_AUTHOR_DISPLAY_NAME}\nAuthor Eamil : ${env.CHANGE_AUTHOR_EMAIL}\n Message: ```${customMsg}```\n${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}"

    slackSend channel: 'checkmarx-pipeline' , color: color, message: msg
}


def check_app_workspace_path(app_path_list){
   def app_dir = "."
   app_path_list = app_path_list.dropRight(1)
   if(app_path_list.size() <1){
   return app_dir // return app default workspace path
   }
   //exists = true
   pom_path = "./${app_path_list.join('/')}/pom.xml"
   def exists = fileExists "${pom_path}"
   if(exists){
       return app_path_list.join('/') + '/' // return app workspace path
   }
  return check_app_workspace_path(app_path_list)
}


def change_app_dir(sourceChanged){
  def app_dir='.'
  if(sourceChanged.size()){
    def is_app_path = false
    def app_path=''
    int max_count = 0
    for (int i = 0; i < sourceChanged.size(); i++) {
    if(sourceChanged[i].contains('/') ){
        int count = sourceChanged[i].length() - sourceChanged[i].replaceAll("/","").length() // check max depth path of file
        if(max_count<count){
            max_count = count
            is_app_path=true
            app_path = sourceChanged[i]
        }
    }
    }
    if(is_app_path){
    app_path_list =  app_path.split('/') //
    app_dir = check_app_workspace_path(app_path_list)
    return  app_dir
  }
  }
  return app_dir
}

def checkmarx_scan(app_name, msg){
  try {
    step([$class: 'CxScanBuilder', comment: '', credentialsId: '', excludeFolders: '', excludeOpenSourceFolders: '', exclusionsSetting: 'global', failBuildOnNewResults: false, failBuildOnNewSeverity: 'HIGH', filterPattern: '''!**/_cvs/**/*, !**/.svn/**/*,   !**/.hg/**/*,   !**/.git/**/*,  !**/.bzr/**/*, !**/bin/**/*,
    !**/obj/**/*,  !**/backup/**/*, !**/.idea/**/*, !**/*.DS_Store, !**/*.ipr,     !**/*.iws,
    !**/*.bak,     !**/*.tmp,       !**/*.aac,      !**/*.aif,      !**/*.iff,     !**/*.m3u, !**/*.mid, !**/*.mp3,
    !**/*.mpa,     !**/*.ra,        !**/*.wav,      !**/*.wma,      !**/*.3g2,     !**/*.3gp, !**/*.asf, !**/*.asx,
    !**/*.avi,     !**/*.flv,       !**/*.mov,      !**/*.mp4,      !**/*.mpg,     !**/*.rm,  !**/*.swf, !**/*.vob,
    !**/*.wmv,     !**/*.bmp,       !**/*.gif,      !**/*.jpg,      !**/*.png,     !**/*.psd, !**/*.tif, !**/*.swf,
    !**/*.jar,     !**/*.zip,       !**/*.rar,      !**/*.exe,      !**/*.dll,     !**/*.pdb, !**/*.7z,  !**/*.gz,
    !**/*.tar.gz,  !**/*.tar,       !**/*.gz,       !**/*.ahtm,     !**/*.ahtml,   !**/*.fhtml, !**/*.hdm,
    !**/*.hdml,    !**/*.hsql,      !**/*.ht,       !**/*.hta,      !**/*.htc,     !**/*.htd, !**/*.war, !**/*.ear,
    !**/*.htmls,   !**/*.ihtml,     !**/*.mht,      !**/*.mhtm,     !**/*.mhtml,   !**/*.ssi, !**/*.stm,
    !**/*.stml,    !**/*.ttml,      !**/*.txn,      !**/*.xhtm,     !**/*.xhtml,   !**/*.class, !**/*.iml, !Checkmarx/Reports/*.*
        ''', fullScanCycle: 10, groupId: '00000000-1111-1111-b111-989c9070eb11', includeOpenSourceFolders: '', osaArchiveIncludePatterns: '*.zip, *.war, *.ear, *.tgz', osaInstallBeforeScan: false, password: '{AQAAABAAAAAQwc/A7qGEf8W2LE6Xd2eseEqWadUYPfMKO50vqs9hiL8=}', preset: '36', projectName: app_name, sastEnabled: true, serverUrl: 'https://checkmarx.copart.com', sourceEncoding: '1', username: '', vulnerabilityThresholdResult: 'FAILURE', waitForResultsEnabled: true])
      }
  catch(e) {
    currentBuild.result = 'FAILURE'
    msg = e
    throw e
  }
  finally {
    if (currentBuild.result == 'FAILURE' && !msg) {
      msg = "Failing the stage due to CxSAST Scan ${currentBuild.result}"
      throw new Exception(msg)
    }
    if(!msg){
      currentBuild.result = 'SUCCESS'
      msg = "CxSAST Scan ${currentBuild.result}"
      if (env.BRANCH_NAME && !env.BRANCH_NAME.startsWith("PR-")){
      notifySlack(currentBuild.result, "CxSAST Scan: ${msg}", app_name)
    }
       }

        }
    return msg
}

pipeline {
  agent {
    node {
        label 'docker_agents'
    }
  }
  parameters {
      booleanParam(name: 'SKIP_TEST', defaultValue: false, description: 'Skip Test')
  }

  tools {
        maven 'Maven 3.3.3'
        jdk 'OpenJDK 11.0.6'
    }

  stages {
    stage('PREP') {
      steps {
        cleanWs()
        checkout([
          $class: 'GitSCM',
          branches: scm.branches,
          doGenerateSubmoduleConfigurations: false,
          extensions: scm.extensions + [
            [$class: 'CleanBeforeCheckout'],
            [$class: 'SubmoduleOption', disableSubmodules: false, recursiveSubmodules: true, reference: '', trackingSubmodules: false]
          ],
          submoduleCfg: [],
          userRemoteConfigs: scm.userRemoteConfigs])
          script {
             //only for PR Branches
            if (env.BRANCH_NAME && env.BRANCH_NAME.startsWith("PR-")) {
                        List<String> sourceChanged = sh(returnStdout: true, script: "git  --no-pager diff --name-only origin/${env.CHANGE_TARGET}").split()
                        echo "${sourceChanged}"
                        // String log = sh(returnStdout: true, script: "git log")
                        // echo "${log}"
                        app_workspace = change_app_dir(sourceChanged)
                        echo "Changed to workspace ${app_workspace}"
                        sh """
                        cd ${app_workspace}
                        """
            }
            // only for PR Merged Branches
            else   {
                        echo "${env.BRANCH_NAME}"
                        List<String> sourceMerged = sh(returnStdout: true, script: 'git rev-list --min-parents=1 --max-count=1 HEAD | git log -m -1 --name-only --pretty="format:"').split()
                        echo "${sourceMerged}"
                        commitid = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                        echo "${commitid}"
                        short_sha = sh(returnStdout: true, script: 'git rev-parse  --short HEAD').trim()
                        echo "${short_sha}"
                        app_workspace = change_app_dir(sourceMerged)
                        echo "Changed to workspace ${app_workspace}"
                        sh """
                        cd ${app_workspace}
                        """
                        }
                        if(app_workspace == '.') {
                          app_pom_path = "./pom.xml"
                          deploy_json_path = "./Deployment-target.json"
                        }
                        else {
                          app_pom_path = "./${app_workspace}pom.xml"
                          deploy_json_path = "./Deployment-target.json"
                       }
                        def pom = readMavenPom file: app_pom_path
                        app_name = pom.artifactId
                        app_version = pom.version.split('-')[0]
                        try {
                          deploymentJson = readJSON file: deploy_json_path
                        }
                        catch(FileNotFoundException ex){
                          echo "${ex}"
                          DEPLOY= false
                        }
                        if(DEPLOY && deploymentJson.containsKey('envs')){
                        for(int i=0; i < deploymentJson['envs'].size(); i++){
                        deploymenttTrack = deploymentJson['envs']
                      }
                     }
                    else { DEPLOY = false }
            }
      }
      post {
          failure {
              echo 'Failed cloning the repo'
          }
      }
    }
    stage('Test') {
      when {
      expression { env.BRANCH_NAME.startsWith("PR-") && params.SKIP_TEST != true }
      }
      steps {
        sh """
        cd ${app_workspace}
        mvn clean compile test
        """
    }
    }


stage("SonarQube"){
  when {
  expression { env.BRANCH_NAME.startsWith("PR-") }
  }
  steps{
    withSonarQubeEnv(credentialsId: '33f52f12-e934-4aaa-9cea-68b4243d445e',
    installationName:'Sonar' ){
      sh """
      cd ${app_workspace}
      mvn clean compile sonar:sonar
      """
  }
}
}

stage("Quality Gate") {
  when {
  expression { env.BRANCH_NAME.startsWith("PR-") }
  }
        steps {
          timeout(time: 5, unit: 'MINUTES') {
            waitForQualityGate(webhookSecretId: 'Sonar-Secret' , abortPipeline: true)
          }
      }
}

stage('Build & Push image'){
  when {
    expression { env.BRANCH_NAME && env.BRANCH_NAME ==~/.*master.*|.*test_release.*|.*release-\d\.\d+/ }
    }
  environment {
    SHORT_SHA = "${short_sha}"
  }
  steps {
    script {
        sh """
                cd ${app_workspace}
                mvn clean install package  -DcreateChecksum=true -U -DskipTests
           """
         }
         logbook enabled: true, gitCommitSha: commitid
  }
}

stage('Deploy'){
  when {
    expression { env.BRANCH_NAME && env.BRANCH_NAME ==~/.*master.*|.*test_release.*|.*release-\d\.\d+/ && DEPLOY}
    }
  steps {
    ws("${env.workspace}/${app_workspace}"){
    script {
      IMAGE_TAG = "${app_version}.${env.BUILD_NUMBER}-${short_sha}"
      for(int i=0; i < deploymentJson['envs'].size(); i++){
       TRACK = deploymenttTrack[i]['track']
       STACK =  deploymenttTrack[i]['stack']
       def webhook_name = deploymenttTrack[i]['app']
          def payload = """{
                        "secret": "SuperSecret",
                        "stack": "${STACK}",
                        "track": "${TRACK}",
                        "artifacts": [
                          {
                            "type": "docker/image",
                            "name": "dockerregistry.copart.com/copart/${app_name}",
                            "version": "${IMAGE_TAG}",
                            "artifactAccount": "docker-registry",
                            "reference": "dockerregistry.copart.com/copart/${app_name}:${IMAGE_TAG}"
                          }
                        ]
                      }"""
         echo "${payload}"
          // sh "curl -v -X POST -H 'Content-type: application/json' -d '${payload}' https://rnq-spinnaker.k8s.copart.com/gate/webhooks/webhook/demo-deploy --insecure"
         def url = "https://rnq-spinnaker.k8s.copart.com/gate/webhooks/webhook/${webhook_name}"
         response = httpRequest contentType: 'APPLICATION_JSON', httpMode: 'POST', requestBody: payload, url: url, ignoreSslErrors: true, customHeaders: [[name: 'X-Spinnaker-Secret', value: 'NXsytwluSdJfzmsr8TbkM']], validResponseCodes: '200', quiet: true
         echo "status: ${response.status}"
       }
         }
     }
  }
}

stage ('Checkmarx')
  {
    when {
      expression { env.BRANCH_NAME && env.BRANCH_NAME ==~/.*master.*|.*release-\d\.\d+/}
      }
    options {
      timeout(time: 10, unit: 'MINUTES')
      //skipDefaultCheckout()
    }
    agent {
      node {
          label 'rncbld412'
      }
    }
    steps {
      sh """
      cd ${app_workspace}
      """

      script {
      if(app_workspace == '.'){
        try {
        checkmarx_scan(app_name, msg)
      } catch(e) {
        msg = e
        throw e
      }
      }
      else {
      ws("${env.workspace}/${app_workspace}"){
        try{
        checkmarx_scan(app_name, msg)
      } catch(e) {
        msg = e
        throw e
      }
      }
     }
   }
 }

  post {
        failure {
          script{
          if (env.BRANCH_NAME && !env.BRANCH_NAME.startsWith("PR-")){
         notifySlack(currentBuild.result, "CxSAST Scan: ${msg}", app_name)
       }
     }
        }
        always{
          deleteDir() /* clean up our workspace */
        }
    }
}

}

post {
    always {
      // CLEAN UP WS
      cleanWs()
      deleteDir()
    }
  }

}

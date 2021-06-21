pipeline {
  agent {
    kubernetes {
      cloud "openshift"
      label "jenkins-slave-${UUID.randomUUID().toString()}"
      serviceAccount "jenkins"
      defaultContainer 'jnlp'
      yaml """
      apiVersion: v1
      kind: Pod
      metadata:
        labels:
          app: "jenkins-${env.JOB_BASE_NAME}"
          indy-pipeline-build-number: "${env.BUILD_NUMBER}"
      spec:
        containers:
        - name: sidecar
          image: quay.io/kaine/indy-sidecar:latest
          imagePullPolicy: Always
          volumeMounts:
          - mountPath: /deployments/config/proxy.yaml
            readOnly: true
            name: sidecar-config
            subPath: proxy.yaml
          - mountPath: /deployments/config/application.yaml
            readOnly: true
            name: sidecar-config
            subPath: application.yaml
          env:
            - name: JAVA_OPTS
              value: '-Xms3G -Xmx3G -Xss256k'
          ports:
            - containerPort: 8080
              protocol: TCP
        - name: jnlp
          image: quay.io/factory2/jenkins-agent:maven-36-rhel7-latest
          imagePullPolicy: Always
          tty: true
          env:
          - name: HOME
            value: /home/jenkins-11-openjdk
          volumeMounts:
          - mountPath: /home/jenkins/sonatype
            name: volume-0
          - mountPath: /mnt/ocp
            name: volume-2
          - mountPath: /home/jenkins-11-openjdk/.m2/settings.xml
            subPath: settings.xml
            name: sidecar-config
          workingDir: /home/jenkins-11-openjdk
        volumes:
        - name: volume-0
          secret:
            defaultMode: 420
            secretName: sonatype-secrets
        - name: volume-2
          configMap:
            defaultMode: 420
            name: jenkins-openshift-mappings
        - name: sidecar-config
          configMap:
            defaultMode: 420
            name: sidecar-jenkinsci-config
      """
    }
  }
  stages {
    stage('git checkout') {
      steps{
        script{
          checkout([$class      : 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false,
                    extensions  : [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'galley'], [$class: 'CleanCheckout']],
                    submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/commonjava/galley']]])
        }
      }
    }
    stage('build test'){
      steps {
        dir('galley'){
          sh 'mvn -s /home/jenkins-11-openjdk/.m2/settings.xml clean install'
        }
      }
    }
    //TODO: archive decompress test & trace log export test
  }
}
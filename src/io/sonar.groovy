@Library('1c-utils')

import io.libs.V8Utils

def utils = new V8Utils(this)
    uccode = new Random().nextInt(100)

    String jobName = System.getenv('JOB_NAME')

    pipeline {
        parameters { 
            booleanParam(name: 'ECHO_OFF', defaultValue: (params?.ECHO_OFF != null ? params.ECHO_OFF : (env.ECHO_OFF != null ? env.ECHO_OFF.toBoolean() : true)), description: 'Disable command echo in Windows bat')
            booleanParam(name: 'LOCK', defaultValue: (params?.LOCK != null ? params.LOCK : (env.LOCK != null ? env.LOCK.toBoolean() : false)), description: 'Disable command echo in Windows bat')
            booleanParam(name: 'RUN_TESTS', defaultValue: (params?.RUN_TESTS != null ? params.RUN_TESTS : (env.RUN_TESTS != null ? env.RUN_TESTS.toBoolean() : true)), description: 'Run test stages (scenario/smoke/unit/cleanup/allure)')
            string(name: 'CREDENTIALS_ID_GIT', defaultValue: (params?.CREDENTIALS_ID_GIT ?: (env.CREDENTIALS_ID_GIT ?: 'CREDENTIALS_ID_GIT')), description: 'Credentials ID for git steps')
            string(name: 'projectKey_sonar', defaultValue: (params?.projectKey_sonar ?: (env.projectKey_sonar ?: '1c_arch')), description: 'Ключ проекта sonar')
            string(name: 'url_sonar', defaultValue: (params?.url_sonar ?: (env.url_sonar ?: 'localhost')), description: 'УРЛ проекта sonar')
            string(name: 'token_sonar', defaultValue: (params?.token_sonar ?: (env.token_sonar ?: '')), description: 'УРЛ проекта sonar')
        }
        
        agent { label 'localhost1' }
        stages {


            stage('Скачиваем конфигурацию из гит') {
                steps {
                script {
                    git branch: env.branch, credentialsId: CREDENTIALS_ID_GIT, url: env.rep_git
                }
                }
            }

              
            stage('Запуск сканирования Sonar'){
                steps{
                    script{

                        returnCode = utils.cmd("sonar-scanner -Dsonar.projectKey=${projectKey_sonar} -Dsonar.sources=. -Dsonar.host.url=http://${url_sonar}:9000  -Dsonar.token=${token_sonar}");
                    
                    }    
                } 
            }  
     }   

    post {
        failure {
            script {
                    messageText = 'Сборка завершена неудачно.'
            }
        }
    }
    }

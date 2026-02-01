def CREDENTIALS_ID_BASE = (params?.CREDENTIALS_ID_BASE ?: (env.CREDENTIALS_ID_BASE ?: 'CREDENTIALS_ID_BASE'))
def CREDENTIALS_ID_GIT = (params?.CREDENTIALS_ID_GIT ?: (env.CREDENTIALS_ID_GIT ?: 'CREDENTIALS_ID_GIT'))

@Library('1c-utils')

import io.libs.V8Utils

def utils = new V8Utils(this)
    uccode = new Random().nextInt(100)

    String jobName = System.getenv('JOB_NAME')

    pipeline {
        parameters {
            booleanParam(name: 'ECHO_OFF', defaultValue: true, description: 'Disable command echo in Windows bat')
            string(name: 'CREDENTIALS_ID_BASE', defaultValue: 'CREDENTIALS_ID_BASE', description: 'Credentials ID for base steps')
            string(name: 'CREDENTIALS_ID_GIT', defaultValue: 'CREDENTIALS_ID_GIT', description: 'Credentials ID for git steps')
        }
        
        agent { label 'localhost' }
        stages {
            stage('Скачиваем конфигурацию из гит') {
                steps {
                script {
                    git branch: 'develop', credentialsId: CREDENTIALS_ID_GIT, url: 'https://gitverse.ru/kuzin_roman/lesson_14_full_deploy.git'
                }
                }
            }
        stage('Lock sheduledjobs') {
            steps {
                script {
                        withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                            returnCode = utils.vrunner.scheduledJobsLock()
                        }

                    if (returnCode != 0) {
                        error 'Ошибка'
                    }
                }
            }
        }

        stage('Блокируем сеансы, убираем людей') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        returnCode = utils.vrunner.sessionKill(uccode)
                    }
                    if (returnCode != 0) {
                        error 'Ошибка'
                    }
                }
            }
        }
        stage('Собираем конфигурацию из исходников') {
                    steps {
                        script {
                            withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                            utils.buildCF('', uccode)}
                        }
                    }
        }

            stage('Build CFE') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        returnCode = utils.vrunner.compileExt("${WORKSPACE}\\src\\cfe\\yaxunit", "YAXUNIT", uccode)
                        }
                    if (returnCode != 0) {
                            error 'Ошибка'
                    }
                    }
                }
            }

            stage('Обновляем базу') {
                    steps {
                        script {
                            withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    utils.updatedb(uccode)}
                        }
                    }
            }

            stage('Разблокируем сеансы') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        returnCode = utils.vrunner.sessionUnlock(uccode)
                    }
                    if (returnCode != 0) {
                        error 'Ошибка'
                    }
                }
            }
            }

            stage('Запуск сценарных тестов') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    returnCode = utils.vrunner.runVanessa()}
                    }
                }
            }

        stage('Запуск дымовых тестов') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    returnCode = utils.vrunner.runXunit()}
                    }
                }
        }

            stage('Запуск юнит-тестов') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_BASE, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    returnCode = utils.vrunner.runUnitTests("${WORKSPACE}/tools/JSON/yaxunit.json")}
                    }
                }
            }

        stage('Формируем отчет Allure') {
                    steps {
                        allure([
                        includeProperties: false,
                        jdk: '',
                        properties: [],
                        reportBuildPolicy: 'ALWAYS',
                        results: [[path: 'build/out']]
                    ])
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


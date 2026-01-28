    @Library('1c-utils')

    import io.libs.v8_utils

utils = new v8_utils()
    uccode = new Random().nextInt(100)

    String jobName = System.getenv('JOB_NAME')

    pipeline {
        agent { label 'localhost' }
        stages {
            stage('Скачиваем конфигурацию из гит') {
                steps {
                script {
                    // git branch: 'develop', credentialsId: 'token1', url: "https://gitverse.ru/kuzin_roman/1c_architect.git"
                    git branch: 'develop', credentialsId: 'token1', url: 'https://gitverse.ru/kuzin_roman/lesson_14_full_deploy.git'
                }
                }
            }
        stage('Lock sheduledjobs') {
            steps {
                script {
                        withCredentials([usernamePassword(credentialsId: 'Logopass', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                            returnCode = utils.cmd("vrunner scheduledjobs lock --ras ${server1c}:1545 --db ${database} --db-user ${USERNAME} --db-pwd ${PASSWORD} --v8version \"${v8version}\"")
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
                    withCredentials([usernamePassword(credentialsId: 'Logopass', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        returnCode = utils.cmd("vrunner session kill --ras ${server1c}:1545 --db ${database} --uccode \"${uccode}\" --db-user ${USERNAME} --db-pwd ${PASSWORD}  --v8version \"${v8version}\"")
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
                            withCredentials([usernamePassword(credentialsId: 'Logopass', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                            utils.buildCF('', uccode)}
                        }
                    }
        }

            stage('Build CFE') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: 'Logopass', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        returnCode = utils.cmd("vrunner compileext \"${WORKSPACE}\\src\\cfe\\yaxunit\" --updatedb  \"YAXUNIT\" --ibconnection /S${server1c}\\${database} --db-user ${USERNAME} --db-pwd ${PASSWORD} --v8version \"${v8version}\" --uccode \"${uccode}\"")
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
                    utils.updatedb(uccode)
                        }
                    }
            }

            stage('Разблокируем сеансы') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'Logopass', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        returnCode = utils.cmd("vrunner session unlock --ras ${server1c}:1545 --db ${database} --db-user ${USERNAME} --db-pwd ${PASSWORD} --v8version \"${v8version}\" --uccode \"${uccode}\"")
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
                    returnCode = utils.cmd("vrunner vanessa --ibconnection \"/S${server1c}\\${database}\" --db-user ${USERNAME} --db-pwd ${PASSWORD}")
                    }
                }
            }

        stage('Запуск дымовых тестов') {
                steps {
                    script {
                    returnCode = utils.cmd("vrunner xunit --ibconnection \"/S${server1c}\\${database}\" --db-user ${USERNAME} --db-pwd ${PASSWORD} --v8version \"${v8version}\"")
                    }
                }
        }

            stage('Запуск юнит-тестов') {
                steps {
                    script {
                    returnCode = utils.cmd("vrunner run --command RunUnitTests=\"${WORKSPACE}/tools/JSON/yaxunit.json\" --ibconnection \"/S${server1c}\\${database}\" --db-user ${USERNAME} --db-pwd ${PASSWORD} --v8version \"${v8version}\"")
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


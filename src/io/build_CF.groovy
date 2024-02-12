@Library('1c-utils')

import io.libs.v8_utils

utils = new v8_utils()


String jobName = System.getenv('JOB_NAME')
currentBuild.displayName = branchName

pipeline {
    agent { label "${agent_machine}"}
    stages{
        

        stage('Checkout GIT, basic'){
            steps{
                  script{
                    withCredentials([usernamePassword(credentialsId: 'fixed',
                 usernameVariable: 'username',
                 passwordVariable: 'password')]){
                 git branch: branchName, credentialsId: 'fixed', url: "${env.stash}"
                 }
                }
            }  
        } 

        stage('Build CF'){                         
                steps{
                    script{
                        
                        utils.buildCF(env.WORKSPACE, uccode)
                       }
                }
            
        }

        stage('Update DB'){                     
                steps{
                    script{
                       utils.updatedb(uccode)
                    }
                }
        }  


        stage('Build CFE'){                     
                steps{
                    script{
                       utils.buildCFE(env.WORKSPACE,uccode)
                    }
                }
        }

        
    }

    post{
        success{
            node ("master") {
           
            script{
                messageText = "Успешно. Сервер: ${env.server1c} База: ${env.database} "
            
            }
            telegramSend(message: "${messageText}", chatId: -1001212659133)
            }
            failure{

                node ("master") {
            
                telegramSend(message: "${messageText}", chatId: -1001212659133)
                }  
            
            } 
        }
    }

}

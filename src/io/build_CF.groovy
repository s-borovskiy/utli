def credentialsId = (params?.CREDENTIALS_ID ?: (env.CREDENTIALS_ID ?: 'Logopass'))
@Library('1c-utils')

import io.libs.V8Utils

def utils = new V8Utils(this)


String jobName = System.getenv('JOB_NAME')
currentBuild.displayName = branchName

pipeline {
        parameters {
            string(name: 'CREDENTIALS_ID', defaultValue: 'Logopass', description: 'Credentials ID for all steps')
        }
    agent { label "${agent_machine}"}
    stages{
        

        stage('Checkout GIT, basic'){
            steps{
                  script{
                    withCredentials([usernamePassword(credentialsId: credentialsId,
                 usernameVariable: 'username',
                 passwordVariable: 'password')]){
                 git branch: branchName, credentialsId: credentialsId, url: "${env.stash}"
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

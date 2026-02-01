def CREDENTIALS_ID_BASE = (params?.CREDENTIALS_ID_BASE ?: (env.CREDENTIALS_ID_BASE ?: 'Logopass'))
def CREDENTIALS_ID_GIT = (params?.CREDENTIALS_ID_GIT ?: (env.CREDENTIALS_ID_GIT ?: CREDENTIALS_ID_BASE))
def CREDENTIALS_ID_HRAN = (params?.CREDENTIALS_ID_HRAN ?: (env.CREDENTIALS_ID_HRAN ?: CREDENTIALS_ID_BASE))

@Library('1c-utils')

import io.libs.V8Utils

def utils = new V8Utils(this)

pipeline {

      
   agent { label "localhost"}
   stages{
        
        stage('Init repo'){
            steps{
                script{
                

                returnCode = utils.shell.runOrError("cd /D \"${rep_git_local}\" & git checkout -B \"storage_1c\" \"origin/storage_1c\"", 'Ошибка')

                
                withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_GIT,
                usernameVariable: 'username',
                passwordVariable: 'password')]){
                returnCode = utils.shell.runOrError("cd /D \"${rep_git_local}\" & git pull https://${utils.urlEncode(username)}:${utils.urlEncode(password)}@$rep_git_remote storage_1c", 'Ошибка')
                }              
                 withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_HRAN,
                usernameVariable: 'login_hran',
                passwordVariable: 'pass_hran')]){     
                returnCode = utils.hran.init(rep_1c, rep_git_local+"\\src\\cf", "")
                if (returnCode != 0) {
                        error 'Ошибка' 
                    }
                } 
                }   
            }  
        }
        
        stage('sync repo'){
            steps{
                script{
                     withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_HRAN,
                usernameVariable: 'login_hran',
                passwordVariable: 'pass_hran')]){
                    returnCode = utils.hran.sync(rep_1c, rep_git_local+"\\src\\cf", 
                        "https://"+rep_git_remote,"","","");
                    if (returnCode != 0) { error 'Ошибка' }
                     } 
                }   
            }  
        }
        
        
        stage('push repo'){
            steps{
                script{
                    withCredentials([usernamePassword(credentialsId: CREDENTIALS_ID_GIT,
                        usernameVariable: 'username',
                        passwordVariable: 'password')]){
                        returnCode = utils.shell.runOrError("cd /D \"${rep_git_local}\"  & git push https://${utils.urlEncode(username)}:${utils.urlEncode(password)}@$rep_git_remote", 'Ошибка')
                    }
                }    
            }  
        }

        stage('branch install'){
        steps{
            script{                 
                utils.shell.run("cd /D \"${rep_git_local}\" & \"C:\\Program Files\\Git\\bin\\bash.exe\" build_branch.sh") 
            }    
        }  
}

        
         
   }    
}

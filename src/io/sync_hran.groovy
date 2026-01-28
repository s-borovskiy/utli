@Library('1c-utils')

import io.libs.v8_utils

def utils = new v8_utils(this)

pipeline {
   agent { label "localhost"}
   stages{
        
        stage('Init repo'){
            steps{
                script{
                

                returnCode = utils.shell.runOrError("cd /D \"${rep_git_local}\" & git checkout -B \"storage_1c\" \"origin/storage_1c\"", 'Ошибка')

                
                withCredentials([usernamePassword(credentialsId: 'token1',
                usernameVariable: 'username',
                passwordVariable: 'password')]){
                returnCode = utils.shell.runOrError("cd /D \"${rep_git_local}\" & git pull https://${utils.urlEncode(username)}:${utils.urlEncode(password)}@$rep_git_remote storage_1c", 'Ошибка')
                }              
                 withCredentials([usernamePassword(credentialsId: '0589d420-2253-48ad-af53-7b8875f4c99c',
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
                     withCredentials([usernamePassword(credentialsId: '0589d420-2253-48ad-af53-7b8875f4c99c',
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
                    withCredentials([usernamePassword(credentialsId: "token1",
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

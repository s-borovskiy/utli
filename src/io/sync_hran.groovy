@Library('1c-utils')

import io.libs.v8_utils

utils = new v8_utils()

pipeline {
   agent { label "localhost"}
   stages{
        
        stage('Init repo'){
            steps{
                script{
                

                returnCode = utils.cmd("cd /D \"${rep_git_local}\" & git checkout -B \"storage_1c\" \"origin/storage_1c\"");
                if (returnCode != 0) {
                        error 'Ошибка' 
                    }

                
                withCredentials([usernamePassword(credentialsId: 'token1',
                usernameVariable: 'login_hran',
                passwordVariable: 'pass_hran')]){
                returnCode = utils.cmd("cd /D \"${rep_git_local}\" & git pull https://$username:$password@$rep_git_remote storage_1c");
                }
                if (returnCode != 0) {
                        error 'Ошибка' 
                    }              
                 withCredentials([usernamePassword(credentialsId: '0589d420-2253-48ad-af53-7b8875f4c99c',
                usernameVariable: 'username',
                passwordVariable: 'password')]){     
                returnCode = utils.init_hran(rep_1c, rep_git_local+"\\src\\cf",,"");
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
                    returnCode = utils.sync_hran(rep_1c, rep_git_local+"\\src\\cf", 
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
                        returnCode = utils.cmd("cd /D \"${rep_git_local}\"  & git push https://$username:$password@$rep_git_remote")
                    }
                    if (returnCode != 0) { error 'Ошибка' }
                }    
            }  
        }

        stage('branch install'){
        steps{
            script{                 
                utils.cmd("cd /D \"${rep_git_local}\" & \"C:\\Program Files\\Git\\bin\\bash.exe\" build_branch.sh") 
            }    
        }  
}

        
         
   }    
}

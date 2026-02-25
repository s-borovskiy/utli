@Library('1c-utils')

import io.libs.V8Utils

def utils = new V8Utils(this)

pipeline {
    parameters {
        booleanParam(name: 'ECHO_OFF', defaultValue: true, description: 'Disable command echo in Windows bat')
        string(name: 'CREDENTIALS_ID_DB', defaultValue: (params?.CREDENTIALS_ID_DB ?: (env.CREDENTIALS_ID_DB ?: 'CREDENTIALS_ID_DB')), description: 'Credentials ID for DB auth')
        string(name: 'CREDENTIALS_ID_IBCMD', defaultValue: (params?.CREDENTIALS_ID_IBCMD ?: (env.CREDENTIALS_ID_IBCMD ?: (params?.CREDENTIALS_ID_DB ?: (env.CREDENTIALS_ID_DB ?: 'CREDENTIALS_ID_DB')))), description: 'Credentials ID for ibcmd --user/--password')

        choice(name: 'DBMS', choices: ['MSSQLServer', 'PostgreSQL'], description: 'Database engine')
        choice(name: 'DB_TOOL', choices: ['auto', 'sqlcmd', 'psql', 'ibcmd'], description: 'DB client tool (auto maps DBMS -> sqlcmd/psql)')
        string(name: 'IBCMD_PATH', defaultValue: (params?.IBCMD_PATH ?: (env.IBCMD_PATH ?: 'ibcmd')), description: 'Path to ibcmd executable')
        string(name: 'DB_HOST', defaultValue: (params?.DB_HOST ?: (env.DB_HOST ?: (env.server1c ?: 'localhost'))), description: 'DB host')
        string(name: 'DB_NAME', defaultValue: (params?.DB_NAME ?: (env.DB_NAME ?: (env.database ?: 'Prosloyka'))), description: 'Source DB name for backup')
        string(name: 'DB_TARGET', defaultValue: (params?.DB_TARGET ?: (env.DB_TARGET ?: 'Prosloyka_copy')), description: 'Target DB name for restore')

        string(name: 'BACKUP_DIR', defaultValue: (params?.BACKUP_DIR ?: (env.BACKUP_DIR ?: 'C:\\temp\\db_backups')), description: 'Folder where backup file will be stored')
        string(name: 'BACKUP_NAME', defaultValue: (params?.BACKUP_NAME ?: (env.BACKUP_NAME ?: 'db_backup')), description: 'Backup file prefix (without extension)')
    }

    agent { label 'localhost' }

    stages {
        stage('Prepare') {
            steps {
                script {
                    if (!params.DB_HOST?.trim()) {
                        error 'DB_HOST is required'
                    }
                    if (!params.DB_NAME?.trim()) {
                        error 'DB_NAME is required'
                    }
                    if (!params.DB_TARGET?.trim()) {
                        error 'DB_TARGET is required'
                    }
                    if (params.DB_NAME.equalsIgnoreCase(params.DB_TARGET)) {
                        error 'DB_TARGET must be different from DB_NAME'
                    }
                    if (!params.BACKUP_DIR?.trim()) {
                        error 'BACKUP_DIR is required'
                    }

                    if (params.DB_TOOL == 'auto') {
                        env.EFFECTIVE_DB_TOOL = params.DBMS == 'MSSQLServer' ? 'sqlcmd' : 'psql'
                    } else {
                        env.EFFECTIVE_DB_TOOL = params.DB_TOOL
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'sqlcmd' && params.DBMS != 'MSSQLServer') {
                        error 'sqlcmd can be used only with DBMS=MSSQLServer'
                    }
                    if (env.EFFECTIVE_DB_TOOL == 'psql' && params.DBMS != 'PostgreSQL') {
                        error 'psql can be used only with DBMS=PostgreSQL'
                    }

                    def mkdirCmd = "if not exist ${utils.escapeArg(params.BACKUP_DIR.trim())} mkdir ${utils.escapeArg(params.BACKUP_DIR.trim())}"
                    utils.shell.runOrError(mkdirCmd, 'Unable to create backup directory')

                    def backupNameRaw = params.BACKUP_NAME?.trim() ? params.BACKUP_NAME.trim() : params.DB_NAME.trim()
                    def backupNameSafe = backupNameRaw.replaceAll('[^a-zA-Z0-9._-]', '_')
                    def extension = env.EFFECTIVE_DB_TOOL == 'sqlcmd' ? '.bak' : (env.EFFECTIVE_DB_TOOL == 'psql' ? '.sql' : '.dt')
                    env.BACKUP_FILE_PATH = "${params.BACKUP_DIR.trim()}\\${backupNameSafe}_${env.BUILD_NUMBER}${extension}"

                    if (env.EFFECTIVE_DB_TOOL == 'ibcmd') {
                        if (!params.IBCMD_PATH?.trim()) {
                            error 'IBCMD_PATH is required for ibcmd'
                        }
                        if (!params.CREDENTIALS_ID_IBCMD?.trim()) {
                            error 'CREDENTIALS_ID_IBCMD is required for ibcmd'
                        }
                    }

                    writeFile(file: 'backup_path.txt', text: env.BACKUP_FILE_PATH + "\r\n")
                    echo "Backup path: ${env.BACKUP_FILE_PATH}"
                }
            }
        }

        stage('Backup DB_NAME') {
            steps {
                script {
                    def runBackup = { String dbUser = '', String dbPassword = '', String ibcmdUser = '', String ibcmdPassword = '' ->
                        def options = [
                            tool: env.EFFECTIVE_DB_TOOL,
                            host: params.DB_HOST.trim(),
                            database: params.DB_NAME.trim(),
                            backupTarget: env.BACKUP_FILE_PATH,
                            dbms: params.DBMS,
                            ibcmdPath: params.IBCMD_PATH.trim(),
                            ibcmdUser: ibcmdUser,
                            ibcmdPassword: ibcmdPassword,
                            username: dbUser,
                            password: dbPassword
                        ]

                        int returnCode = utils.backupDb(options)
                        if (returnCode != 0) {
                            error "Backup failed. Check sqlcmd_log.txt, psql_log.txt or ibcmd_log.txt in workspace."
                        }
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'sqlcmd') {
                        runBackup('', '')
                        return
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'psql') {
                        if (!params.CREDENTIALS_ID_DB?.trim()) {
                            error 'CREDENTIALS_ID_DB is required for psql'
                        }
                        withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                            withEnv(["PGPASSWORD=${DB_PASSWORD}"]) {
                                runBackup(DB_USER, DB_PASSWORD, '', '')
                            }
                        }
                        return
                    }

                    if (!params.CREDENTIALS_ID_IBCMD?.trim()) {
                        error 'CREDENTIALS_ID_IBCMD is required for ibcmd'
                    }

                    withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_IBCMD, usernameVariable: 'IBCMD_USER', passwordVariable: 'IBCMD_PASSWORD')]) {
                        def ibcmdUser = IBCMD_USER
                        def ibcmdPassword = IBCMD_PASSWORD
                        if (!params.CREDENTIALS_ID_DB?.trim()) {
                            error 'CREDENTIALS_ID_DB is required for ibcmd --db-user/--db-pwd'
                        }
                        withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                            runBackup(DB_USER, DB_PASSWORD, ibcmdUser, ibcmdPassword)
                        }
                    }
                }
            }
        }

        stage('Restore to DB_TARGET') {
            steps {
                script {
                    def runRestore = { String dbUser = '', String dbPassword = '', String ibcmdUser = '', String ibcmdPassword = '' ->
                        def options = [
                            tool: env.EFFECTIVE_DB_TOOL,
                            host: params.DB_HOST.trim(),
                            database: params.DB_TARGET.trim(),
                            backupTarget: env.BACKUP_FILE_PATH,
                            dbms: params.DBMS,
                            ibcmdPath: params.IBCMD_PATH.trim(),
                            ibcmdUser: ibcmdUser,
                            ibcmdPassword: ibcmdPassword,
                            username: dbUser,
                            password: dbPassword
                        ]

                        int returnCode = utils.restoreDb(options)
                        if (returnCode != 0) {
                            error "Restore failed. Check sqlcmd_log.txt, psql_log.txt or ibcmd_log.txt in workspace."
                        }
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'sqlcmd') {
                        runRestore('', '')
                        return
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'psql') {
                        if (!params.CREDENTIALS_ID_DB?.trim()) {
                            error 'CREDENTIALS_ID_DB is required for psql'
                        }
                        withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                            withEnv(["PGPASSWORD=${DB_PASSWORD}"]) {
                                runRestore(DB_USER, DB_PASSWORD, '', '')
                            }
                        }
                        return
                    }

                    if (!params.CREDENTIALS_ID_IBCMD?.trim()) {
                        error 'CREDENTIALS_ID_IBCMD is required for ibcmd'
                    }

                    withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_IBCMD, usernameVariable: 'IBCMD_USER', passwordVariable: 'IBCMD_PASSWORD')]) {
                        def ibcmdUser = IBCMD_USER
                        def ibcmdPassword = IBCMD_PASSWORD
                        if (!params.CREDENTIALS_ID_DB?.trim()) {
                            error 'CREDENTIALS_ID_DB is required for ibcmd --db-user/--db-pwd'
                        }
                        withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                            runRestore(DB_USER, DB_PASSWORD, ibcmdUser, ibcmdPassword)
                        }
                    }
                }
            }
        }
    }
}

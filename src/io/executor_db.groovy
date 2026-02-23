@Library('1c-utils')

import io.libs.V8Utils

def utils = new V8Utils(this)

pipeline {
    parameters {
        booleanParam(name: 'ECHO_OFF', defaultValue: true, description: 'Disable command echo in Windows bat')
        string(name: 'CREDENTIALS_ID_DB', defaultValue: (params?.CREDENTIALS_ID_DB ?: (env.CREDENTIALS_ID_DB ?: 'CREDENTIALS_ID_DB')), description: 'Credentials ID for DB auth')

        choice(name: 'DB_TOOL', choices: ['sqlcmd', 'psql'], description: 'DB client tool')
        string(name: 'DB_HOST', defaultValue: (params?.DB_HOST ?: (env.DB_HOST ?: (env.server1c ?: 'localhost'))), description: 'DB host')
        string(name: 'DB_PORT', defaultValue: (params?.DB_PORT ?: (env.DB_PORT ?: '')), description: 'DB port (optional)')
        string(name: 'DB_NAME', defaultValue: (params?.DB_NAME ?: (env.DB_NAME ?: (env.database ?: 'Prosloyka'))), description: 'Source DB name for backup')
        string(name: 'DB_TARGET', defaultValue: (params?.DB_TARGET ?: (env.DB_TARGET ?: 'Prosloyka_copy')), description: 'Target DB name for restore')

        string(name: 'BACKUP_DIR', defaultValue: (params?.BACKUP_DIR ?: (env.BACKUP_DIR ?: 'C:\\temp\\db_backups')), description: 'Folder where backup file will be stored')
        string(name: 'BACKUP_NAME', defaultValue: (params?.BACKUP_NAME ?: (env.BACKUP_NAME ?: 'db_backup')), description: 'Backup file prefix (without extension)')

        booleanParam(name: 'SQLCMD_INTEGRATED_AUTH', defaultValue: (params?.SQLCMD_INTEGRATED_AUTH != null ? params.SQLCMD_INTEGRATED_AUTH : (env.SQLCMD_INTEGRATED_AUTH != null ? env.SQLCMD_INTEGRATED_AUTH.toBoolean() : true)), description: 'Use -E for sqlcmd')
        booleanParam(name: 'USE_CREDENTIALS', defaultValue: (params?.USE_CREDENTIALS != null ? params.USE_CREDENTIALS : (env.USE_CREDENTIALS != null ? env.USE_CREDENTIALS.toBoolean() : true)), description: 'Use Jenkins DB credentials (mainly for psql)')
        string(name: 'PSQL_MAINTENANCE_DB', defaultValue: (params?.PSQL_MAINTENANCE_DB ?: (env.PSQL_MAINTENANCE_DB ?: 'postgres')), description: 'Maintenance database for psql DROP/CREATE DATABASE')
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

                    if (!params.DB_PORT?.trim()) {
                        env.EFFECTIVE_DB_PORT = params.DB_TOOL == 'sqlcmd' ? '1433' : '5432'
                    } else {
                        env.EFFECTIVE_DB_PORT = params.DB_PORT.trim()
                    }

                    def mkdirCmd = "if not exist ${utils.escapeArg(params.BACKUP_DIR.trim())} mkdir ${utils.escapeArg(params.BACKUP_DIR.trim())}"
                    utils.shell.runOrError(mkdirCmd, 'Unable to create backup directory')

                    def backupNameRaw = params.BACKUP_NAME?.trim() ? params.BACKUP_NAME.trim() : params.DB_NAME.trim()
                    def backupNameSafe = backupNameRaw.replaceAll('[^a-zA-Z0-9._-]', '_')
                    def extension = params.DB_TOOL == 'sqlcmd' ? '.bak' : '.sql'
                    env.BACKUP_FILE_PATH = "${params.BACKUP_DIR.trim()}\\${backupNameSafe}_${env.BUILD_NUMBER}${extension}"

                    writeFile(file: 'backup_path.txt', text: env.BACKUP_FILE_PATH + "\r\n")
                    echo "Backup path: ${env.BACKUP_FILE_PATH}"
                }
            }
        }

        stage('Backup DB_NAME') {
            steps {
                script {
                    def runBackup = { String dbUser = '', String dbPassword = '' ->
                        def options = [
                            tool: params.DB_TOOL,
                            host: params.DB_HOST.trim(),
                            port: env.EFFECTIVE_DB_PORT,
                            database: params.DB_NAME.trim(),
                            backupTarget: env.BACKUP_FILE_PATH,
                            maintenanceDb: params.PSQL_MAINTENANCE_DB.trim(),
                            username: dbUser,
                            password: dbPassword
                        ]

                        int returnCode = utils.backupDb(options)
                        if (returnCode != 0) {
                            error "Backup failed. Check sqlcmd_log.txt or psql_log.txt in workspace."
                        }
                    }

                    def credentialsRequired = params.DB_TOOL == 'sqlcmd' ? !params.SQLCMD_INTEGRATED_AUTH : params.USE_CREDENTIALS
                    if (!credentialsRequired) {
                        runBackup('', '')
                        return
                    }

                    if (!params.CREDENTIALS_ID_DB?.trim()) {
                        error 'CREDENTIALS_ID_DB is required when credentials are enabled'
                    }

                    withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                        if (params.DB_TOOL == 'psql') {
                            withEnv(["PGPASSWORD=${DB_PASSWORD}"]) {
                                runBackup(DB_USER, DB_PASSWORD)
                            }
                        } else {
                            runBackup(DB_USER, DB_PASSWORD)
                        }
                    }
                }
            }
        }

        stage('Restore to DB_TARGET') {
            steps {
                script {
                    def runRestore = { String dbUser = '', String dbPassword = '' ->
                        def options = [
                            tool: params.DB_TOOL,
                            host: params.DB_HOST.trim(),
                            port: env.EFFECTIVE_DB_PORT,
                            database: params.DB_TARGET.trim(),
                            backupTarget: env.BACKUP_FILE_PATH,
                            maintenanceDb: params.PSQL_MAINTENANCE_DB.trim(),
                            username: dbUser,
                            password: dbPassword
                        ]

                        int returnCode = utils.restoreDb(options)
                        if (returnCode != 0) {
                            error "Restore failed. Check sqlcmd_log.txt or psql_log.txt in workspace."
                        }
                    }

                    def credentialsRequired = params.DB_TOOL == 'sqlcmd' ? !params.SQLCMD_INTEGRATED_AUTH : params.USE_CREDENTIALS
                    if (!credentialsRequired) {
                        runRestore('', '')
                        return
                    }

                    if (!params.CREDENTIALS_ID_DB?.trim()) {
                        error 'CREDENTIALS_ID_DB is required when credentials are enabled'
                    }

                    withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                        if (params.DB_TOOL == 'psql') {
                            withEnv(["PGPASSWORD=${DB_PASSWORD}"]) {
                                runRestore(DB_USER, DB_PASSWORD)
                            }
                        } else {
                            runRestore(DB_USER, DB_PASSWORD)
                        }
                    }
                }
            }
        }
    }
}

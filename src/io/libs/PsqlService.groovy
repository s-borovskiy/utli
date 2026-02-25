package io.libs

class PsqlService implements Serializable {
    PipelineContext ctx
    CommandRunner runner

    PsqlService(PipelineContext ctx) {
        this.ctx = ctx
        this.runner = new CommandRunner(ctx)
    }

    int backup(Map options = [:]) {
        def host = requireValue(options.server ?: options.host, "host")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupTarget = requireValue(options.backupTarget ?: options.backupPath, "backupTarget")

        if (ctx.fileExists(backupTarget)) {
            ctx.echo("Backup already exists: ${backupTarget}")
            return 1
        }

        def command = pgDumpCommand(host, options.port, database, options.username, backupTarget)
        return runner.run(command)
    }

    int restore(Map options = [:]) {
        def host = requireValue(options.server ?: options.host, "host")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupTarget = requireValue(options.backupTarget ?: options.backupPath, "backupTarget")
        def maintenanceDb = optionalValue(options.maintenanceDb, "postgres")

        if (!ctx.fileExists(backupTarget)) {
            ctx.error("Backup file not found: ${backupTarget}")
        }

        def terminateSql = "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
            "WHERE datname = ${pgsqlString(database)} AND pid <> pg_backend_pid();"
        def terminateCode = runner.run(psqlCommand(host, options.port, maintenanceDb, options.username, terminateSql))
        if (terminateCode != 0) {
            return terminateCode
        }

        def dropSql = "DROP DATABASE IF EXISTS ${pgsqlIdentifier(database)};"
        def dropCode = runner.run(psqlCommand(host, options.port, maintenanceDb, options.username, dropSql))
        if (dropCode != 0) {
            return dropCode
        }

        def createSql = "CREATE DATABASE ${pgsqlIdentifier(database)};"
        def createCode = runner.run(psqlCommand(host, options.port, maintenanceDb, options.username, createSql))
        if (createCode != 0) {
            return createCode
        }

        return runner.run(psqlFileRestoreCommand(host, options.port, database, options.username, backupTarget))
    }

    private String psqlCommand(String host, def port, String database, def username, String sql) {
        return "psql -h ${ctx.escapeArg(host)} " +
            (port != null && !port.toString().trim().isEmpty() ? "-p ${ctx.escapeArg(port.toString().trim())} " : "") +
            "-d ${ctx.escapeArg(database)} " +
            (username != null && !username.toString().trim().isEmpty() ? "-U ${ctx.escapeArg(username.toString())} " : "") +
            "-w " +
            "-v ON_ERROR_STOP=1 -X -q " +
            "-L ${ctx.escapeArg(logPath())} " +
            "-c ${ctx.escapeArg(sql)}"
    }

    private String psqlFileRestoreCommand(String host, def port, String database, def username, String backupFile) {
        return "psql -h ${ctx.escapeArg(host)} " +
            (port != null && !port.toString().trim().isEmpty() ? "-p ${ctx.escapeArg(port.toString().trim())} " : "") +
            "-d ${ctx.escapeArg(database)} " +
            (username != null && !username.toString().trim().isEmpty() ? "-U ${ctx.escapeArg(username.toString())} " : "") +
            "-w -v ON_ERROR_STOP=1 -X " +
            "-L ${ctx.escapeArg(logPath())} " +
            "-f ${ctx.escapeArg(backupFile)}"
    }

    private String pgDumpCommand(String host, def port, String database, def username, String backupFile) {
        return "pg_dump -h ${ctx.escapeArg(host)} " +
            (port != null && !port.toString().trim().isEmpty() ? "-p ${ctx.escapeArg(port.toString().trim())} " : "") +
            (username != null && !username.toString().trim().isEmpty() ? "-U ${ctx.escapeArg(username.toString())} " : "") +
            "-d ${ctx.escapeArg(database)} -w -F p -f ${ctx.escapeArg(backupFile)} --no-owner --no-privileges"
    }

    private String requireValue(def value, String optionName) {
        if (value == null || value.toString().trim().isEmpty()) {
            ctx.error("Option '${optionName}' is required")
        }
        return value.toString().trim()
    }

    private String optionalValue(def value, String defaultValue) {
        return value == null || value.toString().trim().isEmpty() ? defaultValue : value.toString().trim()
    }

    private String pgsqlIdentifier(String value) {
        return "\"" + value.toString().replace("\"", "\"\"") + "\""
    }

    private String pgsqlString(String value) {
        return "'" + value.toString().replace("'", "''") + "'"
    }

    private String logPath() {
        def workspace = ctx.env("WORKSPACE")
        return workspace?.trim() ? "${workspace}\\psql_log.txt" : "psql_log.txt"
    }
}

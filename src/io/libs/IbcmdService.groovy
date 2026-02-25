package io.libs

class IbcmdService implements Serializable {
    PipelineContext ctx
    CommandRunner runner

    IbcmdService(PipelineContext ctx) {
        this.ctx = ctx
        this.runner = new CommandRunner(ctx)
    }

    int backup(Map options = [:]) {
        def server = requireValue(options.server ?: options.host, "server")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupTarget = requireValue(options.backupTarget ?: options.backupPath, "backupTarget")
        def dbms = normalizeDbms(options.dbms ?: options.ibcmdDbms ?: options.databaseEngine)
        def dataDir = requireValue(options.ibcmdDataDir ?: options.dataDir, "ibcmdDataDir")
        def ibcmdPath = optionalValue(options.ibcmdPath, "ibcmd")
        def ibcmdUser = options.ibcmdUser ?: options.infobaseUser
        def ibcmdPassword = options.ibcmdPassword ?: options.infobasePassword
        def dbServer = formatDbServer(server, options.port, dbms)

        if (ctx.fileExists(backupTarget)) {
            ctx.echo("Backup already exists: ${backupTarget}")
            return 1
        }

        def command = "${ctx.escapeArg(ibcmdPath)} infobase dump " +
            "--db-server=${ctx.escapeArg(dbServer)} " +
            "--dbms=${ctx.escapeArg(dbms)} " +
            option("db-user", options.username) +
            option("db-pwd", options.password) +
            option("user", ibcmdUser) +
            option("password", ibcmdPassword) +
            "--db-name=${ctx.escapeArg(database)} " +
            "--data=${ctx.escapeArg(dataDir)} " +
            "${ctx.escapeArg(backupTarget)} " +
            "> ${ctx.escapeArg(logPath())} 2>&1"

        return runner.run(command)
    }

    int restore(Map options = [:]) {
        def server = requireValue(options.server ?: options.host, "server")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupTarget = requireValue(options.backupTarget ?: options.backupPath, "backupTarget")
        def dbms = normalizeDbms(options.dbms ?: options.ibcmdDbms ?: options.databaseEngine)
        def dataDir = requireValue(options.ibcmdDataDir ?: options.dataDir, "ibcmdDataDir")
        def ibcmdPath = optionalValue(options.ibcmdPath, "ibcmd")
        def ibcmdUser = options.ibcmdUser ?: options.infobaseUser
        def ibcmdPassword = options.ibcmdPassword ?: options.infobasePassword
        def dbServer = formatDbServer(server, options.port, dbms)

        if (!ctx.fileExists(backupTarget)) {
            ctx.error("Backup file not found: ${backupTarget}")
        }

        def command = "${ctx.escapeArg(ibcmdPath)} infobase restore " +
            "--db-server=${ctx.escapeArg(dbServer)} " +
            "--dbms=${ctx.escapeArg(dbms)} " +
            option("db-user", options.username) +
            option("db-pwd", options.password) +
            option("user", ibcmdUser) +
            option("password", ibcmdPassword) +
            "--db-name=${ctx.escapeArg(database)} " +
            "--data=${ctx.escapeArg(dataDir)} " +
            "${ctx.escapeArg(backupTarget)} " +
            "> ${ctx.escapeArg(logPath())} 2>&1"

        return runner.run(command)
    }

    private String normalizeDbms(def rawDbms) {
        def dbms = rawDbms == null ? "" : rawDbms.toString().trim().toLowerCase()
        if (dbms in ["mssql", "mssqlserver", "sqlserver"]) {
            return "MSSQLServer"
        }
        if (dbms in ["postgres", "postgresql", "pgsql"]) {
            return "PostgreSQL"
        }
        ctx.error("Unsupported ibcmd DBMS '${rawDbms}'. Allowed values: MSSQLServer, PostgreSQL")
        return ""
    }

    private String formatDbServer(String server, def port, String dbms) {
        def value = server.toString().trim()
        if (port == null || port.toString().trim().isEmpty()) {
            return value
        }
        def portValue = port.toString().trim()
        return dbms == "PostgreSQL" ? "${value}:${portValue}" : "${value},${portValue}"
    }

    private String option(String optionName, def value) {
        if (value == null || value.toString().trim().isEmpty()) {
            return ""
        }
        return "--${optionName}=${ctx.escapeArg(value.toString())} "
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

    private String logPath() {
        def workspace = ctx.env("WORKSPACE")
        return workspace?.trim() ? "${workspace}\\ibcmd_log.txt" : "ibcmd_log.txt"
    }
}

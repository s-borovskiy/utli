package io.libs

class DB_method implements Serializable {
    PipelineContext ctx
    CommandRunner runner

    DB_method(PipelineContext ctx) {
        this.ctx = ctx
        this.runner = new CommandRunner(ctx)
    }

    int backupDB(Map options = [:]) {
        def tool = normalizeTool(options.tool ?: options.engine ?: options.client)
        if (tool == "sqlcmd") {
            return backupSqlServer(options)
        }
        return backupPostgres(options)
    }

    int restoreDB(Map options = [:]) {
        def tool = normalizeTool(options.tool ?: options.engine ?: options.client)
        if (tool == "sqlcmd") {
            return restoreSqlServer(options)
        }
        return restorePostgres(options)
    }

    // Backward compatibility for legacy callers.
    int backupDB(String serverSql, String baseName, String backupFullPath) {
        return backupDB([
            tool: "sqlcmd",
            server: serverSql,
            database: baseName,
            backupTarget: backupFullPath
        ])
    }

    // Backward compatibility for legacy callers.
    int restoreDB(String serverSql, String baseName, String backupName, Object internal = null) {
        return restoreDB([
            tool: "sqlcmd",
            server: serverSql,
            database: baseName,
            backupTarget: backupName
        ])
    }

    private int backupSqlServer(Map options) {
        def server = requireValue(options.server ?: options.host, "server")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupTarget = requireValue(options.backupTarget ?: options.backupPath, "backupTarget")

        if (ctx.fileExists(backupTarget)) {
            ctx.echo("Backup already exists: ${backupTarget}")
            return 1
        }

        def sql = "BACKUP DATABASE ${mssqlIdentifier(database)} TO DISK = ${mssqlString(backupTarget)} " +
            "WITH COPY_ONLY, INIT, COMPRESSION, STATS = 10;"

        def command = "sqlcmd -S ${ctx.escapeArg(sqlServerHost(server, options.port))} " +
            "${sqlcmdAuthArgs(options.username, options.password)} -b -Q ${ctx.escapeArg(sql)} " +
            "-o ${ctx.escapeArg(sqlcmdLogPath())}"

        return runner.run(command)
    }

    private int restoreSqlServer(Map options) {
        def server = requireValue(options.server ?: options.host, "server")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupTarget = requireValue(options.backupTarget ?: options.backupPath, "backupTarget")

        def dbName = mssqlIdentifier(database)
        def dbLiteral = mssqlString(database)
        def backupLiteral = mssqlString(backupTarget)
        def sql = "BEGIN TRY " +
            "IF DB_ID(${dbLiteral}) IS NULL BEGIN RAISERROR('Database not found', 16, 1); RETURN; END;" +
            "ALTER DATABASE ${dbName} SET SINGLE_USER WITH ROLLBACK IMMEDIATE;" +
            "RESTORE DATABASE ${dbName} FROM DISK = ${backupLiteral} WITH REPLACE, STATS = 10;" +
            "ALTER DATABASE ${dbName} SET MULTI_USER;" +
            "END TRY BEGIN CATCH " +
            "IF DB_ID(${dbLiteral}) IS NOT NULL BEGIN TRY ALTER DATABASE ${dbName} SET MULTI_USER; END TRY BEGIN CATCH END CATCH END;" +
            "THROW; END CATCH;"

        def command = "sqlcmd -S ${ctx.escapeArg(sqlServerHost(server, options.port))} " +
            "${sqlcmdAuthArgs(options.username, options.password)} -b -Q ${ctx.escapeArg(sql)} " +
            "-o ${ctx.escapeArg(sqlcmdLogPath())}"

        return runner.run(command)
    }

    private int backupPostgres(Map options) {
        def host = requireValue(options.server ?: options.host, "host")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupDb = requireValue(options.backupTarget ?: options.backupName, "backupTarget")
        if (database.equalsIgnoreCase(backupDb)) {
            ctx.error("backupTarget must be different from database for psql mode")
        }

        def maintenanceDb = optionalValue(options.maintenanceDb, "postgres")
        def sql = "DO \$\$ BEGIN " +
            "IF EXISTS (SELECT 1 FROM pg_database WHERE datname = ${pgsqlString(backupDb)}) THEN " +
            "RAISE EXCEPTION 'Database % already exists', ${pgsqlString(backupDb)}; " +
            "END IF; END \$\$;" +
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
            "WHERE datname = ${pgsqlString(database)} AND pid <> pg_backend_pid();" +
            "CREATE DATABASE ${pgsqlIdentifier(backupDb)} WITH TEMPLATE ${pgsqlIdentifier(database)};"

        return runner.run(psqlCommand(host, options.port, maintenanceDb, options.username, sql))
    }

    private int restorePostgres(Map options) {
        def host = requireValue(options.server ?: options.host, "host")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupDb = requireValue(options.backupTarget ?: options.backupName, "backupTarget")
        def maintenanceDb = optionalValue(options.maintenanceDb, "postgres")

        def sql = "DO \$\$ BEGIN " +
            "IF NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = ${pgsqlString(backupDb)}) THEN " +
            "RAISE EXCEPTION 'Backup database % does not exist', ${pgsqlString(backupDb)}; " +
            "END IF; END \$\$;" +
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
            "WHERE datname IN (${pgsqlString(database)}, ${pgsqlString(backupDb)}) AND pid <> pg_backend_pid();" +
            "DROP DATABASE IF EXISTS ${pgsqlIdentifier(database)};" +
            "CREATE DATABASE ${pgsqlIdentifier(database)} WITH TEMPLATE ${pgsqlIdentifier(backupDb)};"

        return runner.run(psqlCommand(host, options.port, maintenanceDb, options.username, sql))
    }

    private String normalizeTool(def rawTool) {
        def tool = rawTool == null ? "" : rawTool.toString().trim().toLowerCase()
        if (tool in ["sqlcmd", "mssql", "sqlserver"]) {
            return "sqlcmd"
        }
        if (tool in ["psql", "postgres", "postgresql"]) {
            return "psql"
        }
        ctx.error("Unsupported DB tool '${rawTool}'. Allowed values: sqlcmd, psql")
        return ""
    }

    private String sqlcmdAuthArgs(def username, def password) {
        if (username == null || username.toString().trim().isEmpty()) {
            return "-E"
        }
        def user = username.toString()
        def pass = password == null ? "" : password.toString()
        return "-U ${ctx.escapeArg(user)} -P ${ctx.escapeArg(pass)}"
    }

    private String sqlServerHost(String server, def port) {
        def value = server.toString().trim()
        if (port != null && !port.toString().trim().isEmpty()) {
            return "${value},${port.toString().trim()}"
        }
        return value
    }

    private String psqlCommand(String host, def port, String database, def username, String sql) {
        def command = "psql -h ${ctx.escapeArg(host)} " +
            (port != null && !port.toString().trim().isEmpty() ? "-p ${ctx.escapeArg(port.toString().trim())} " : "") +
            "-d ${ctx.escapeArg(database)} " +
            (username != null && !username.toString().trim().isEmpty() ? "-U ${ctx.escapeArg(username.toString())} " : "") +
            "-w " +
            "-v ON_ERROR_STOP=1 -X -q " +
            "-L ${ctx.escapeArg(psqlLogPath())} " +
            "-c ${ctx.escapeArg(sql)}"

        return command
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

    private String mssqlIdentifier(String value) {
        return "[" + value.toString().replace("]", "]]") + "]"
    }

    private String mssqlString(String value) {
        return "N'" + value.toString().replace("'", "''") + "'"
    }

    private String pgsqlIdentifier(String value) {
        return "\"" + value.toString().replace("\"", "\"\"") + "\""
    }

    private String pgsqlString(String value) {
        return "'" + value.toString().replace("'", "''") + "'"
    }

    private String sqlcmdLogPath() {
        def workspace = ctx.env("WORKSPACE")
        return workspace?.trim() ? "${workspace}\\sqlcmd_log.txt" : "sqlcmd_log.txt"
    }

    private String psqlLogPath() {
        def workspace = ctx.env("WORKSPACE")
        return workspace?.trim() ? "${workspace}\\psql_log.txt" : "psql_log.txt"
    }
}

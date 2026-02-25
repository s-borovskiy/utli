package io.libs

class SqlcmdService implements Serializable {
    PipelineContext ctx
    CommandRunner runner

    SqlcmdService(PipelineContext ctx) {
        this.ctx = ctx
        this.runner = new CommandRunner(ctx)
    }

    int backup(Map options = [:]) {
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
            "-o ${ctx.escapeArg(logPath())}"

        return runner.run(command)
    }

    int restore(Map options = [:]) {
        def server = requireValue(options.server ?: options.host, "server")
        def database = requireValue(options.database ?: options.dbName, "database")
        def backupTarget = requireValue(options.backupTarget ?: options.backupPath, "backupTarget")

        def dbLiteral = mssqlString(database)
        def backupLiteral = mssqlString(backupTarget)
        def sql = "USE master;" +
            "DECLARE @db nvarchar(128) = ${dbLiteral};" +
            "DECLARE @back_file nvarchar(4000) = ${backupLiteral};" +
            "DECLARE @dataPath nvarchar(4000) = CONVERT(nvarchar(4000), SERVERPROPERTY('InstanceDefaultDataPath'));" +
            "DECLARE @logPath nvarchar(4000) = CONVERT(nvarchar(4000), SERVERPROPERTY('InstanceDefaultLogPath'));" +
            "IF (@dataPath IS NULL OR LTRIM(RTRIM(@dataPath)) = '') " +
            "SELECT TOP 1 @dataPath = LEFT(physical_name, LEN(physical_name) - CHARINDEX('\\', REVERSE(physical_name)) + 1) " +
            "FROM master.sys.master_files WHERE database_id = 1 AND type_desc = 'ROWS';" +
            "IF (@logPath IS NULL OR LTRIM(RTRIM(@logPath)) = '') " +
            "SELECT TOP 1 @logPath = LEFT(physical_name, LEN(physical_name) - CHARINDEX('\\', REVERSE(physical_name)) + 1) " +
            "FROM master.sys.master_files WHERE database_id = 1 AND type_desc = 'LOG';" +
            "IF RIGHT(@dataPath, 1) NOT IN ('\\', '/') SET @dataPath = @dataPath + '\\';" +
            "IF RIGHT(@logPath, 1) NOT IN ('\\', '/') SET @logPath = @logPath + '\\';" +
            "IF DB_ID(@db) IS NOT NULL BEGIN " +
            "DECLARE @dropSql nvarchar(max) = N'ALTER DATABASE ' + QUOTENAME(@db) + N' SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ' + QUOTENAME(@db) + N';';" +
            "EXEC(@dropSql);" +
            "END;" +
            "CREATE TABLE #restoreFiles (" +
            "    LogicalName NVARCHAR(128)," +
            "    PhysicalName NVARCHAR(260)," +
            "    Type CHAR(1)," +
            "    FileGroupName NVARCHAR(128)," +
            "    Size NUMERIC(20, 0)," +
            "    MaxSize NUMERIC(20, 0)," +
            "    FileID BIGINT," +
            "    CreateLSN NUMERIC(25, 0)," +
            "    DropLSN NUMERIC(25, 0) NULL," +
            "    UniqueID UNIQUEIDENTIFIER," +
            "    ReadOnlyLSN NUMERIC(25, 0) NULL," +
            "    ReadWriteLSN NUMERIC(25, 0) NULL," +
            "    BackupSizeInBytes BIGINT," +
            "    SourceBlockSize INT," +
            "    FileGroupID INT," +
            "    LogGroupGUID UNIQUEIDENTIFIER NULL," +
            "    DifferentialBaseLSN NUMERIC(25, 0) NULL," +
            "    DifferentialBaseGUID UNIQUEIDENTIFIER," +
            "    IsReadOnly BIT," +
            "    IsPresent BIT," +
            "    TDEThumbprint varbinary(32)," +
            "    SnapshotURL nvarchar(360) NULL);" +
            "DECLARE @fileListSql nvarchar(max) = N'RESTORE FILELISTONLY FROM DISK = N''' + REPLACE(@back_file, '''', '''''') + N'''';" +
            "INSERT INTO #restoreFiles EXEC(@fileListSql);" +
            "DECLARE @logicalDataFile NVARCHAR(128), @logicalLogFile NVARCHAR(128);" +
            "SELECT TOP 1 @logicalDataFile = LogicalName FROM #restoreFiles WHERE Type = 'D' ORDER BY FileID;" +
            "SELECT TOP 1 @logicalLogFile = LogicalName FROM #restoreFiles WHERE Type = 'L' ORDER BY FileID;" +
            "IF (@logicalDataFile IS NULL OR @logicalLogFile IS NULL) BEGIN RAISERROR('Cannot read logical file names from backup', 16, 1); RETURN; END;" +
            "DECLARE @mdf nvarchar(4000) = @dataPath + @db + N'.mdf';" +
            "DECLARE @ldf nvarchar(4000) = @logPath + @db + N'_log.ldf';" +
            "DECLARE @restoreSql nvarchar(max) = N'RESTORE DATABASE ' + QUOTENAME(@db) + N' FROM DISK = N''' + REPLACE(@back_file, '''', '''''') + N''' " +
            "WITH FILE = 1, MOVE N''' + REPLACE(@logicalDataFile, '''', '''''') + N''' TO N''' + REPLACE(@mdf, '''', '''''') + N''', " +
            "MOVE N''' + REPLACE(@logicalLogFile, '''', '''''') + N''' TO N''' + REPLACE(@ldf, '''', '''''') + N''', REPLACE, STATS = 10;';" +
            "EXEC(@restoreSql);"

        def command = "sqlcmd -S ${ctx.escapeArg(sqlServerHost(server, options.port))} " +
            "${sqlcmdAuthArgs(options.username, options.password)} -b -Q ${ctx.escapeArg(sql)} " +
            "-o ${ctx.escapeArg(logPath())}"

        return runner.run(command)
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

    private String requireValue(def value, String optionName) {
        if (value == null || value.toString().trim().isEmpty()) {
            ctx.error("Option '${optionName}' is required")
        }
        return value.toString().trim()
    }

    private String mssqlIdentifier(String value) {
        return "[" + value.toString().replace("]", "]]") + "]"
    }

    private String mssqlString(String value) {
        return "N'" + value.toString().replace("'", "''") + "'"
    }

    private String logPath() {
        def workspace = ctx.env("WORKSPACE")
        return workspace?.trim() ? "${workspace}\\sqlcmd_log.txt" : "sqlcmd_log.txt"
    }
}

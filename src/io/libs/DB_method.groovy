package io.libs

class DB_method implements Serializable {
    PipelineContext ctx
    SqlcmdService sqlcmd
    PsqlService psql
    IbcmdService ibcmd

    DB_method(PipelineContext ctx) {
        this.ctx = ctx
        this.sqlcmd = new SqlcmdService(ctx)
        this.psql = new PsqlService(ctx)
        this.ibcmd = new IbcmdService(ctx)
    }

    int backupDB(Map options = [:]) {
        def tool = normalizeTool(options.tool ?: options.engine ?: options.client)
        if (tool == "sqlcmd") {
            return sqlcmd.backup(options)
        }
        if (tool == "ibcmd") {
            return ibcmd.backup(options)
        }
        return psql.backup(options)
    }

    int restoreDB(Map options = [:]) {
        def tool = normalizeTool(options.tool ?: options.engine ?: options.client)
        if (tool == "sqlcmd") {
            return sqlcmd.restore(options)
        }
        if (tool == "ibcmd") {
            return ibcmd.restore(options)
        }
        return psql.restore(options)
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

    private String normalizeTool(def rawTool) {
        def tool = rawTool == null ? "" : rawTool.toString().trim().toLowerCase()
        if (tool in ["sqlcmd", "mssql", "sqlserver"]) {
            return "sqlcmd"
        }
        if (tool in ["psql", "postgres", "postgresql"]) {
            return "psql"
        }
        if (tool in ["ibcmd", "1c", "1c-ib"]) {
            return "ibcmd"
        }
        ctx.error("Unsupported DB tool '${rawTool}'. Allowed values: sqlcmd, psql, ibcmd")
        return ""
    }
}

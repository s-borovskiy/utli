package io.libs

class VRunnerService implements Serializable {
    PipelineContext ctx
    CommandRunner runner

    VRunnerService(PipelineContext ctx) {
        this.ctx = ctx
        this.runner = new CommandRunner(ctx)
    }

    int buildCF(String dir = "", Object uccode = "") {
        def workspace = dir?.isEmpty() ? ctx.env("WORKSPACE") : dir
        def codeValue = uccode == null ? "" : uccode.toString()
        def command = "vrunner compile --src \"${workspace}\\src\\cf\" -c --ibconnection /S${ctx.env("server1c")}/${ctx.env("database")} --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))} --v8version \"${ctx.env("v8version")}\" --uccode \"${codeValue}\" "
        def code = runner.run(command)
        if (code > 0) {
            ctx.error('РСЃС…РѕРґРЅРёРєРё РЅРµ СЃРѕР±СЂР°Р»РёСЃСЊ:\n' + loadErrorMessage())
        }
        return code
    }

    int buildCFE(String dir = "", Object uccode = "") {
        def workspace = dir?.isEmpty() ? ctx.env("WORKSPACE") : dir
        def codeValue = uccode == null ? "" : uccode.toString()
        def command = "vrunner compileext \"${workspace}\\src\\cfe\" --ibconnection /S${ctx.env("server1c")}\\${ctx.env("database")} --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))} --v8version \"${ctx.env("v8version")}\" --uccode \"${codeValue}\""
        return runner.run(command)
    }

    int updateDb(Object uccode = "") {
        def codeValue = uccode == null ? "" : uccode.toString()
        def command = "vrunner updatedb --v1 --ibconnection /S${ctx.env("server1c")}/${ctx.env("database")} --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))} --v8version \"${ctx.env("v8version")}\" --uccode \"${codeValue}\" "
        def code = runner.run(command)
        if (code != 0) {
            ctx.error('РћС€РёР±РєР° РїСЂРё СѓРґР°Р»РµРЅРёРё Р±Р°Р·С‹:')
        }
        return code
    }

    int scheduledJobsLock() {
        def command = "vrunner scheduledjobs lock --ras ${ctx.env("server1c")}:1545 --db ${ctx.env("database")} --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))} --v8version \"${ctx.env("v8version")}\""
        return runner.run(command)
    }

    int sessionKill(Object uccode = "") {
        def codeValue = uccode == null ? "" : uccode.toString()
        def command = "vrunner session kill --ras ${ctx.env("server1c")}:1545 --db ${ctx.env("database")} --uccode \"${codeValue}\" --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))}  --v8version \"${ctx.env("v8version")}\""
        return runner.run(command)
    }

    int sessionUnlock(Object uccode = "") {
        def codeValue = uccode == null ? "" : uccode.toString()
        def command = "vrunner session unlock --ras ${ctx.env("server1c")}:1545 --db ${ctx.env("database")} --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))} --v8version \"${ctx.env("v8version")}\" --uccode \"${codeValue}\""
        return runner.run(command)
    }

    int compileExt(String extPath, String updateDbName = "", Object uccode = "") {
        def codeValue = uccode == null ? "" : uccode.toString()
        def updateArg = updateDbName?.isEmpty() ? "" : "--updatedb \"${updateDbName}\" "
        def command = "vrunner compileext \"${extPath}\" ${updateArg}--ibconnection /S${ctx.env("server1c")}\\${ctx.env("database")} --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))} --v8version \"${ctx.env("v8version")}\" --uccode \"${codeValue}\""
        return runner.run(command)
    }

    int runVanessa() {
        def command = "vrunner vanessa --ibconnection \"/S${ctx.env("server1c")}\\${ctx.env("database")}\" --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))}"
        return runner.run(command)
    }

    int runXunit() {
        def command = "vrunner xunit --ibconnection \"/S${ctx.env("server1c")}\\${ctx.env("database")}\" --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))} --v8version \"${ctx.env("v8version")}\""
        return runner.run(command)
    }

    int runUnitTests(String configPath) {
        def command = "vrunner run --command RunUnitTests=\"${configPath}\" --ibconnection \"/S${ctx.env("server1c")}\\${ctx.env("database")}\" --db-user ${ctx.escapeArg(ctx.env("USERNAME"))} --db-pwd ${ctx.escapeArg(ctx.env("PASSWORD"))} --v8version \"${ctx.env("v8version")}\""
        return runner.run(command)
    }

    private String loadErrorMessage() {
        def logFile = "${ctx.env("WORKSPACE")}\\log.txt"
        if (ctx.fileExists(logFile)) {
            return ctx.readFile(logFile)
        }
        return "log.txt not found"
    }
}

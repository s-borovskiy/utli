package io.libs

class V8Utils implements Serializable {
    PipelineContext ctx
    CommandRunner shell
    VRunnerService vrunner
    HranService hran
    TelegramService telegram

    V8Utils(steps) {
        this.ctx = new PipelineContext(steps)
        this.shell = new CommandRunner(ctx)
        this.vrunner = new VRunnerService(ctx)
        this.hran = new HranService(ctx)
        this.telegram = new TelegramService(ctx)
    }

    def getWorkspaceLine(workspace = "") {
        return ctx.workspaceLine(workspace)
    }

    def cmd(command, workDir = "") {
        return shell.run(command, workDir)
    }

    def buildCF(dir = '', uccode = '') {
        return vrunner.buildCF(dir, uccode)
    }

    def buildCFE(dir = '', uccode = '') {
        return vrunner.buildCFE(dir, uccode)
    }

    def updatedb(uccode = '') {
        return vrunner.updateDb(uccode)
    }

    def hello_world() {
        ctx.echo('Hello, world!')
    }

    def sync_hran(rep_1c, rep_git_local, rep_git_remote, ext = "", aditional_parameters = "", server1c = "") {
        return hran.sync(rep_1c, rep_git_local, rep_git_remote, ext, aditional_parameters, server1c)
    }

    def init_hran(rep_1c, rep_git_local, ext = "", server1c = "") {
        return hran.init(rep_1c, rep_git_local, ext, server1c)
    }

    def telegram_send_message(TOKEN, CHAT_ID, messageText, success) {
        telegram.sendMessage(TOKEN, CHAT_ID, messageText, success)
    }

    def escapeArg(value) {
        return ctx.escapeArg(value)
    }

    def urlEncode(value) {
        return ctx.urlEncode(value)
    }
}

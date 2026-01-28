package io.libs

class HranService implements Serializable {
    PipelineContext ctx
    CommandRunner runner

    HranService(PipelineContext ctx) {
        this.ctx = ctx
        this.runner = new CommandRunner(ctx)
    }

    int sync(String rep1c, String repGitLocal, String repGitRemote, String ext = "", String additionalParameters = "", String server1c = "") {
        def command = "gitsync sync --storage-user ${ctx.escapeArg(ctx.env("login_hran"))} --storage-pwd ${ctx.escapeArg(ctx.env("pass_hran"))} ${ext} ${additionalParameters} \"${rep1c}\" \"${repGitLocal}\""
        return runner.run(command)
    }

    int init(String rep1c, String repGitLocal, String ext = "", String server1c = "") {
        def command = "gitsync init --storage-user ${ctx.escapeArg(ctx.env("login_hran"))} --storage-pwd ${ctx.escapeArg(ctx.env("pass_hran"))} ${ext} \"${rep1c}\" \"${repGitLocal}\""
        return runner.run(command)
    }
}

package io.libs

class HranService implements Serializable {
    PipelineContext ctx
    CommandRunner runner

    HranService(PipelineContext ctx) {
        this.ctx = ctx
        this.runner = new CommandRunner(ctx)
    }

    int sync(String rep1c, String repGitLocal, String repGitRemote, String ext = "", String additionalParameters = "", String server1c = "", String storageUser = "", String storagePwd = "") {
        def user = storageUser?.trim() ? storageUser : ctx.env("login_hran")
        def pwd = storagePwd?.trim() ? storagePwd : ctx.env("pass_hran")
        def command = "gitsync sync --storage-user \"${ctx.escapeArg(user)}\" --storage-pwd \"${ctx.escapeArg(pwd)}\" ${ext} ${additionalParameters} \"${rep1c}\" \"${repGitLocal}\""
        return runner.run(command)
    }

    int init(String rep1c, String repGitLocal, String ext = "", String server1c = "", String storageUser = "", String storagePwd = "") {
        //def user = storageUser?.trim() ? storageUser : ctx.env("login_hran")
        //def pwd = storagePwd?.trim() ? storagePwd : ctx.env("pass_hran")
        def command = "gitsync init --storage-user \"${storageUser}\" --storage-pwd \"${storagePwd}\" ${ext} \"${rep1c}\" \"${repGitLocal}\""
        return runner.run(command)
    }
}

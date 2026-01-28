package io.libs

class PipelineContext implements Serializable {
    def steps

    PipelineContext(steps) {
        this.steps = steps
    }

    String env(String name, String defaultValue = "") {
        def value = null
        try {
            value = steps?.env?.get(name)
        } catch (ignored) {
        }
        if (value == null || value.toString().isEmpty()) {
            try {
                if (steps?.binding?.hasVariable(name)) {
                    value = steps.binding.getVariable(name)
                }
            } catch (ignored) {
            }
        }
        return value == null ? defaultValue : value.toString()
    }

    boolean isUnix() {
        return steps.isUnix()
    }

    String workspaceLine(String workspace = "") {
        return workspace?.isEmpty() ? "" : "cd ${workspace} &"
    }

    int run(String command, String workDir = "") {
        def prepared = command
        if (workDir != null && !workDir.isEmpty()) {
            prepared = "${workspaceLine(workDir)} ${command}"
        }
        if (isUnix()) {
            return steps.sh(script: "${prepared}", returnStatus: true)
        }
        return steps.bat(script: "chcp 65001\n ${prepared}", returnStatus: true)
    }

    boolean fileExists(String path) {
        return steps.fileExists(path)
    }

    String readFile(String path) {
        return steps.readFile(file: path)
    }

    void error(String message) {
        steps.error(message)
    }

    void echo(String message) {
        steps.echo(message)
    }
}

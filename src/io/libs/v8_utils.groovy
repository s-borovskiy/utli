package io.libs

class v8_utils extends V8Utils implements Serializable {
    v8_utils(steps) {
        super(steps)
    }

    v8_utils() {
        super(resolveScript())
    }

    private static def resolveScript() {
        try {
            def thread = org.jenkinsci.plugins.workflow.cps.CpsThread.current()
            if (thread != null) {
                def exec = thread.getExecution()
                if (exec != null) {
                    def owner = exec.getOwner()
                    if (owner != null) {
                        def script = owner.getScript()
                        if (script != null) {
                            return script
                        }
                    }
                }
            }
        } catch (ignored) {
        }
        throw new IllegalStateException('v8_utils requires a script context. Use new V8Utils(this) or new v8_utils(this).')
    }
}

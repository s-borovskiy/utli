package io.libs

import java.util.Random

class TelegramService implements Serializable {
    PipelineContext ctx

    TelegramService(PipelineContext ctx) {
        this.ctx = ctx
    }

    void sendMessage(String token, String chatId, String messageText, boolean success) {
        def icons = ["рџ›Ђ", "рџљ§", "рџё", "рџљЂ", "вЊ›", "рџђџ", "рџ’Є", "рџ“Ђ", "рџ“·", "рџђ„", "рџђ€"]
        def randomIndex = (new Random()).nextInt(icons.size())

        messageText = escapeStringForMarkdownV2(messageText)
        if (success == true) {
            messageText = escapeStringForMarkdownV2(messageText)
            messageText = "вњ…вњ…вњ… ${messageText} URL: ${ctx.env("BUILD_URL")}"
        } else {
            messageText = "вќЊвќЊвќЊ ${messageText} URL: ${ctx.env("BUILD_URL")}"
        }

        ctx.steps.sh("""                  curl -s -X POST https://api.telegram.org/bot${token}/sendMessage \
                            -H "Content-type: application/x-www-form-urlencoded; charset=utf-8" \
                            -d chat_id=${chatId} \
                            -d text="${messageText}"
                            """)
    }

    private static String escapeStringForMarkdownV2(String incoming) {
        return incoming.replace('_', '\\_')
            .replace('*', '\\*')
            .replace('[', '\\[')
            .replace(']', '\\]')
            .replace('(', '\\(')
            .replace(')', '\\)')
            .replace('~', '\\~')
            .replace('`', '\\`')
            .replace('>', '\\>')
            .replace('#', '\\#')
            .replace('+', '\\+')
            .replace('-', '\\-')
            .replace('=', '\\=')
            .replace('|', '\\|')
            .replace('{', '\\{')
            .replace('}', '\\}')
    }
}

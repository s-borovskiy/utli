package io.libs

import java.util.Random

class TelegramService implements Serializable {
    PipelineContext ctx

    TelegramService(PipelineContext ctx) {
        this.ctx = ctx
    }

    void sendMessage(String token, String chatId, String messageText, boolean success) {
        def icons = ["🛂", "🚧", "😸", "🚀", "⌛", "🐙", "💪", "📀", "📷", "🐄", "🐀"]
        def randomIndex = (new Random()).nextInt(icons.size())

        messageText = escapeStringForMarkdownV2(messageText)
        if (success == true) {
            messageText = escapeStringForMarkdownV2(messageText)
            messageText = "✅✅✅ ${messageText} URL: ${ctx.env("BUILD_URL")}"
        } else {
            messageText = "❌❌❌ ${messageText} URL: ${ctx.env("BUILD_URL")}"
        }

        ctx.steps.sh("""
            curl -sS -X POST "https://api.telegram.org/bot${token}/sendMessage" \\
                -H "Content-Type: application/json; charset=utf-8" \\
                --data-raw '{\"chat_id\":${chatId},\"text\":\"${messageText.replace("\\\\","\\\\\\\\").replace("\"","\\\\\\"")}\"}'
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

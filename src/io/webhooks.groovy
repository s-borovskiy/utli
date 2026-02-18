pipeline {
  agent { label 'localhost' }

  triggers {
    cron('H/1 * * * *')
  }

  options {
    disableConcurrentBuilds()
  }

  stages {
    stage('Init job properties') {
      steps {
        script {
          // Все настройки — через параметры job
          properties([
            pipelineTriggers([cron('H/1 * * * *')]),
            parameters([
              string(name: 'TG_OFFSET', defaultValue: (params.TG_OFFSET ?: '0'), description: 'Telegram getUpdates offset (internal)'),
              string(name: 'TG_ALLOWED_JOBS', defaultValue: (params.TG_ALLOWED_JOBS ?: 'notif,build,deploy'), description: 'Comma-separated Jenkins jobs allowed to run'),
              string(name: 'TG_ALLOWED_CHAT_ID', defaultValue: (params.TG_ALLOWED_CHAT_ID ?: '-4893940074'), description: 'Allowed Telegram chat_id (empty = allow all)')
            ])
          ])
        }
      }
    }

    stage('Poll Telegram') {
      steps {
        withCredentials([string(credentialsId: 'TG_BOT_TOKEN', variable: 'TG_TOKEN')]) {
          script {
            // читаем настройки из параметров
            def offset = (params.TG_OFFSET ?: '0').trim()
            if (!offset.isInteger()) offset = '0'

            def allowedChatId = (params.TG_ALLOWED_CHAT_ID ?: '').trim()
            def allowedJobs = (params.TG_ALLOWED_JOBS ?: '').split(',')
              .collect { it.trim() }
              .findAll { it }

            echo "Using TG_OFFSET=${offset}"
            echo "Allowed chat_id=${allowedChatId ?: '(any)'}"
            echo "Allowed jobs=${allowedJobs}"

            // getUpdates
            def updatesJson = sh(
              returnStdout: true,
              script: """#!/bin/sh
                set +x
                curl -sS "https://api.telegram.org/bot${TG_TOKEN}/getUpdates?timeout=0&offset=${offset}"
              """
            ).trim()

            writeFile(file: 'updates.json', text: updatesJson)

            // parse updates.json
            def parsed = sh(
              returnStdout: true,
              script: """python - <<'PY'
import json

with open("updates.json", "r", encoding="utf-8") as f:
    data = json.load(f)

res = data.get("result", [])
max_id = None
items = []

for u in res:
    uid = u.get("update_id")
    if uid is not None:
        max_id = uid if max_id is None else max(max_id, uid)

    msg = u.get("message") or {}
    chat_id = (msg.get("chat") or {}).get("id")
    text = (msg.get("text") or "").strip()
    if chat_id is not None and text:
        items.append((chat_id, text))

print("" if max_id is None else str(max_id + 1))
for chat_id, text in items:
    print(f"{chat_id}\\t{text}")
PY
"""
            ).trim()

            def lines = parsed ? parsed.split("\\r?\\n", -1) : []
            def newOffset = (lines.size() > 0) ? lines[0].trim() : ''

            // сохраняем новый offset обратно в job properties (и НЕ теряем остальные параметры)
            if (newOffset) {
              echo "Updating TG_OFFSET -> ${newOffset}"
              properties([
                pipelineTriggers([cron('H/1 * * * *')]),
                parameters([
                  string(name: 'TG_OFFSET', defaultValue: newOffset, description: 'Telegram getUpdates offset (internal)'),
                  string(name: 'TG_ALLOWED_JOBS', defaultValue: (params.TG_ALLOWED_JOBS ?: 'notif,build,deploy'), description: 'Comma-separated Jenkins jobs allowed to run'),
                  string(name: 'TG_ALLOWED_CHAT_ID', defaultValue: (params.TG_ALLOWED_CHAT_ID ?: '-4893940074'), description: 'Allowed Telegram chat_id (empty = allow all)')
                ])
              ])
            }

            def msgs = (lines.size() > 1) ? lines[1..-1] : []
            if (msgs.isEmpty()) {
              echo "No new messages"
              return
            }

            for (def row : msgs) {
              def parts = row.split("\\t", 2)
              if (parts.size() < 2) continue
              def chatId = parts[0].trim()
              def text = parts[1].trim()

              // фильтр по чату (если задан)
              if (allowedChatId && chatId != allowedChatId) continue

              // только /run ...
              if (!text.startsWith("/run")) continue

              def tokens = text.split("\\s+")
              if (tokens.size() < 2) {
                echo "Command without job: ${text}"
                continue
              }

              def jobName = tokens[1].trim()
              if (!allowedJobs.contains(jobName)) {
                echo "Job not allowed: ${jobName}"
                continue
              }

              def paramsList = []
              for (int i=2; i<tokens.size(); i++) {
                def kv = tokens[i].split("=", 2)
                if (kv.size() == 2) {
                  paramsList << string(name: kv[0], value: kv[1])
                }
              }

              echo "Trigger job=${jobName} from chat=${chatId} text=${text}"
              if (paramsList) {
                build job: jobName, parameters: paramsList, wait: false
              } else {
                build job: jobName, wait: false
              }
            }
          }
        }
      }
    }
  }
}

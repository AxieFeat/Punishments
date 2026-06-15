# Punishment Service Common

Shared models, DTOs, and config helpers for punishment-service clients.

## Catalog

The built-in reasons and restriction capabilities are loaded from `punishments.conf`.

Example:

```
punishments {
  capabilities = [
    { key = "server.join", title = "Server access", appliesTo = ["BAN"] },
    { key = "chat.text", title = "Text chat", appliesTo = ["MUTE", "BAN"] },
    { key = "chat.voice", title = "Voice chat", appliesTo = ["MUTE", "BAN"] }
  ]

  reasons = [
    { id = "cheating", title = "Cheating", category = "gameplay", recommendedScopeKeys = ["server.join"] },
    { id = "abuse", title = "Abusive language", category = "chat", recommendedScopeKeys = ["chat.text", "chat.voice"] },
    { id = "spam", title = "Spam", category = "chat", recommendedScopeKeys = ["chat.text"] }
  ]
}
```

## Notes

- Restriction keys are generic strings so the service does not hardcode chat/voice behavior.
- Commands can pass any subset of `restrictionKeys` to specify what to block per punishment.


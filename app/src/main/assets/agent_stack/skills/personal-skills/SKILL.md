---
name: personal-skills
description: Build and maintain skills for this user - the way anything learned is kept for every future chat. Use when asked to remember something, keep a list or a lookup table, write a reusable procedure or script, or create/update a skill.
---

# Personal Skills

A chat ends and takes everything with it. The session workspace is rebuilt from
templates every time it is opened, so a file written there is gone. Two
directories are not:

- `~/.agents/skills/` — the skills, discovered by the catalog in every chat.
- `~/memory/` — the data those skills read and write.

If the user asks you to remember something, keep a list, or be able to do
something again next time, the answer is a skill. Not a note in the reply, not
a file in the workspace.

---

## 1. When to build one

Build a skill when the user says any of: remember this, keep a list of, from now
on, every time I ask for X do Y, save my …, or asks directly for a skill.

Do not build one for a fact that only matters for the current task. A skill the
user did not ask for and will never use is clutter in every future catalog.

Ask for the name only if the user has not implied one. Confirm what you built in
one line; do not read the whole file back.

---

## 2. Shape

```
~/.agents/skills/<skill-name>/
  SKILL.md            the instructions - what it is for and how to use the data
  scripts/<name>.sh   optional, when a lookup or a step is worth automating
~/memory/<skill-name>/
  <whatever>.json     the data itself
```

`SKILL.md` starts with front matter, and the description is what decides whether
a later chat ever opens it. Write it as the trigger, not as a title:

```markdown
---
name: contacts
description: Phone numbers the user has given me. Use before asking for a number, or when opening a chat or a call for someone by name.
---
```

Keep the body short. It says where the data is, what its shape is, and how to
use it. The data does not belong inside `SKILL.md`: a list that grows would make
the model re-read the whole thing on every unrelated turn.

**Data goes in `~/memory/<skill-name>/`, never inside the skill directory.**
Skills that ship with the app are replaced whole on every app update, and one
day this skill may be one of them. Data kept outside survives that.

---

## 3. Building one

Use the shell. Write files with a heredoc, and create both directories first.

```sh
mkdir -p ~/.agents/skills/contacts/scripts ~/memory/contacts
cat > ~/.agents/skills/contacts/SKILL.md <<'EOF'
---
name: contacts
description: Phone numbers the user has given me. Use before asking for a number, or when opening a chat or a call for someone by name.
---

# Contacts

Numbers live in `~/memory/contacts/contacts.json`, as `{"name": "+9725…"}`.
Names are lowercase; match case-insensitively and accept a first name alone.

- Look one up: `sh ~/.agents/skills/contacts/scripts/lookup.sh "dana"`
- Add one: read the file, add the key, write it back whole.
- If a name is missing, ask the user once and add it before continuing.
EOF
echo '{}' > ~/memory/contacts/contacts.json
```

Run a script as an argument to `sh`, never as `./script.sh`. App-private storage
is not executable, so the execute bit does not help and `chmod +x` is wasted.

Verify what you wrote: `cat` the file back once. A skill with broken front matter
does not appear in the catalog at all, and you will not find out until the next
chat.

---

## 4. Scripts

A script earns its place when it saves a turn — a lookup, a formatted intent, a
calculation. It reads from `~/memory/<skill-name>/`, prints one line, and exits.

```sh
cat > ~/.agents/skills/contacts/scripts/lookup.sh <<'EOF'
#!/bin/sh
# usage: lookup.sh <name> - prints the number, or nothing when unknown
name=$(printf '%s' "$1" | tr 'A-Z' 'a-z')
sed -n 's/.*"'"$name"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
  ~/memory/contacts/contacts.json
EOF
```

Prefer a deep link over UI navigation once you have the data. For the example
above, a number resolves straight into a WhatsApp chat:

```
open_intent action=android.intent.action.VIEW uri=https://wa.me/<number without + or spaces>
```

Keep scripts to shell built-ins and what `/system/bin` provides — `sh`, `sed`,
`grep`, `awk`, `cat`. There is no package manager here.

---

## 5. Maintaining them

- **Read before you write.** Rewriting a data file from memory loses the entries
  you did not happen to recall.
- **Update the skill when it turns out to be wrong**, in the same turn you found
  out. A skill that is confidently stale is worse than none.
- **Never edit or overwrite a skill the app ships** — `device-automation`,
  `recovery-and-safety`, `user-preferences`, `app-cards`, `personal-skills`.
  They are replaced whole on every app start and your change would vanish. Build
  a separate skill instead.
- The user can see and delete all of this from the Files sheet in the app, so
  write it as if they will read it. They will.

---
name: commit-signoff
description: Require a model-named Signed-off-by trailer on every commit in this repository.
---

# Commit sign-off

Before creating a commit, set the commit identity to the model doing the work and use `git commit -s`.

The commit must end with a trailer in this form:

```text
Signed-off-by: GPT-6 Astra <gpt-6-astra@users.noreply.github.com>
```

Replace `GPT-6 Astra` and the address with the actual model creating that commit. Do not sign for another model. Verify the trailer with `git show -s --format=%B HEAD` before pushing.

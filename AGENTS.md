# DoomScore repository workflow

The owner requests that routine completed changes accumulate locally and be committed and pushed at midnight in Asia/Kolkata (IST) by the scheduled follow-up in the development chat. Keep delivering requested fixes and APKs during the day, but defer routine commits and pushes unless the owner explicitly requests immediate publication.

Before ending a development turn, explain what is ready for the midnight run and record the relevant verification results in the chat. Do not leave an unfinished change described as ready.

At the scheduled run, review the diff, commit only genuine completed and verified changes with descriptive messages and the actual current timestamp, then push normally to the verified DoomScore remote and branch. Preserve ongoing work and other contributors' changes. Do not force-push or rewrite commit dates. If nothing is ready, skip the run; do not create empty commits or manufacture changes to produce contribution activity.

Keep credentials, signing material, private configuration, APKs, build output and diagnostic reports out of commits. Check the staged diff before committing. GitHub contribution eligibility depends on the commit author date, associated email and eligible branch; pushing an older commit does not move its contribution date.

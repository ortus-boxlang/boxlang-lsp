---
argument-hint: [task-doc]
description: Work on project todos
---

Look at $1 to find the list of tasks. Find the first task with `(Incomplete)` at the end of the title. You can

To get started you MUST:

* Use `grep "###.*\(Incomplete\)" $1` to try and find the first task.
* Ask the user if you need them to clarify anything

While working:

* Start by writing an effective test and then begin development
* Follow the feature-planners instructions as closely as possible
* You must get the user's confirmation that the task is complete before moving on to the next task

When the task is complete, I want you to take the following steps:

1. Add `(Complete)` to the tasks title
2. Update `./development_log.md` with a summary of the work done for the task.
3. Commit the changes to git with a message that includes the task ID and a short description of the task.


Remember, if you need to you can:

* Check the `./development_log.md` file for context on previous work
* If you need to verify a change ask the user to test for you

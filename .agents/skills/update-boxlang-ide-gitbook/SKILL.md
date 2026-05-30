---
name: update-boxlang-ide-gitbook
description: Use this skill to update the BoxLang IDE GitBook by working from a local clone inside this repo at ./boxlang-ide, kept untracked here for easier side-by-side code and docs work.
---

The gitbook documentation is the public facing documentation for the BoxLang project. It is important to keep it up to date with the latest changes and improvements. This skill will help you update the documentation with the latest changes and improvements.

This project's documentation is hosted at https://boxlang-ide.ortusbooks.com.

The git repo for the gitbook is git@github.com:ortus-docs/boxlang-ide.git.

The intended workflow is to clone the gitbook repo into this repository as a local working folder at `./boxlang-ide`, keep that folder untracked in this repo, and use it as a side-by-side docs workspace while making code changes here. Do not use a separate clone elsewhere unless the user explicitly asks for that. Treat `./boxlang-ide` as a workspace convenience clone that lives inside this repo but is never committed to this repo. If it is already cloned, pull the latest changes there. Make documentation edits in that local clone and commit those changes to the gitbook repo itself.

To update the documentation, you can follow these steps:

1. Clone the gitbook repo into this repo as an untracked subfolder if you haven't already: `git clone git@github.com:ortus-docs/boxlang-ide.git ./boxlang-ide`
2. Navigate to the cloned repo: `cd ./boxlang-ide`
3. Pull the latest changes: `git pull origin main`
4. Review the changes in the workspace as well as recent commits. Identify any changes that should be reflected in the documentation. This could include new features, changes to existing features, bug fixes, or any other relevant updates.
5. Update the documentation files in the `./boxlang-ide` repo to reflect the identified changes. This may involve editing existing documentation, adding new sections, or creating new documentation files as needed.
6. After making the necessary updates provide an opportunity for the user to review the changes in the local `./boxlang-ide` clone before committing.
7. Once the changes are reviewed and approved, commit the changes to the gitbook repo.

Some things to remember:

- Ensure that the documentation is clear, concise, and accurately reflects the changes made to the BoxLang project.
- Use appropriate formatting and structure in the documentation to enhance readability and usability.
- Always prefer the local in-repo clone at `./boxlang-ide` because it keeps the docs next to the code being changed and makes the workflow easier.
- Keep the `./boxlang-ide` folder untracked in this repo. Do not add or commit it to the LSP repo.
- Do not turn `./boxlang-ide` into a submodule or otherwise track it from this repo.
- This documentation covers multiple projects. As this is the LSP server documentation, focus on changes related to the LSP server and its features. However, if there are changes in other projects that impact the LSP server or its users, consider including those as well.
- LSP documentation is primarily in the `language-tools` folder but could be in other places as well.

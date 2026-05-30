---
name: update-gitbook-docs
description: Use this skill to update GitBook documentation with the latest changes and improvements.
---

The gitbook documentation is the public facing documentation for the BoxLang project. It is important to keep it up to date with the latest changes and improvements. This skill will help you update the documentation with the latest changes and improvements.

This project's documentation is hosted at https://boxlang-ide.ortusbooks.com.

The git repo for the gitbook is git@github.com:ortus-docs/boxlang-ide.git.

If you need to you can clone the gitbook repo into `./boxlang-ide`. If it is already cloned, you can pull the latest changes.

To update the documentation, you can follow these steps:

1. Clone the gitbook repo if you haven't already: `git clone git@github.com:ortus-docs/boxlang-ide.git ./boxlang-ide`
2. Navigate to the cloned repo: `cd ./boxlang-ide`
3. Pull the latest changes: `git pull origin main`
4. Review the changes in the workspace as well as recent commits. Identify any changes that should be reflected in the documentation. This could include new features, changes to existing features, bug fixes, or any other relevant updates.
5. Update the documentation files in the `./boxlang-ide` repo to reflect the identified changes. This may involve editing existing documentation, adding new sections, or creating new documentation files as needed.
6. After making the necessary updates provide an opportunity for the user to review the changes before committing.
7. Once the changes are reviewed and approved, commit the changes to the gitbook repo.

Some things to remember:

- Ensure that the documentation is clear, concise, and accurately reflects the changes made to the BoxLang project.
- Use appropriate formatting and structure in the documentation to enhance readability and usability.
- This documentation covers multiple projects. As this is the LSP server documentation, focus on changes related to the LSP server and its features. However, if there are changes in other projects that impact the LSP server or its users, consider including those as well.
- LSP documentation is primarily in the `language-tools` folder but could be in other places as well.
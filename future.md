# Future Work

- Split the graph bundle out of the main frontend chunk so the initial app load is smaller.
- Make forgot-password and reset-password failures show inline field-level feedback, the same way login and register do.
- Add a seeded demo account for quicker first-run testing after `docker compose down -v`.
- Improve the movie search UI with keyboard navigation and a selected-result focus state.
- Add an explicit empty state for movie searches and saved connections when no matches exist.
- Consider a dedicated movie detail drawer so the selected graph movie and its connections can be inspected without leaving the search flow.
- Add API tests for the new register and login validation surfaces so error formatting stays consistent.
- When a movie is selected, render the full connection graph for that movie and auto-layout the nodes so the user does not have to drag them into a readable arrangement.

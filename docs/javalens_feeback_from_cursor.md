# JavaLens Feedback From Cursor

Date: 2026-06-09

Context: Cursor agent used `user-jl-strategies-orb` while analyzing the Funnel open-latency issue across
`strategies-orb` and `com-jats2-model`.

## What Worked Well

- `health_check` was immediately useful. It confirmed server readiness, Java version, project load state, workspace
  project count, and enabled capabilities.
- `list_projects` was useful for discovering exact project keys. In this workspace, the relevant keys were
  `strategies-orb` and `com-jats2-model`.
- `analyze_type` worked well for class-level orientation. It returned members, fields, nested types, diagnostics,
  and suggested next tools.
- `analyze_method` was very useful once called with a precise source location. For example, it confirmed that
  `PortfolioManager.ptReceiver.processQuote(...)` is called by `MarketBook.updateLastQuote(...)`, and listed the
  exact hot-path callees: `portfolioThrottled`, `PricingTable.registerMarket`, `MarketPrice.update`,
  `dirtyContractsList.add`, `PricingTable.getFairPrice`, `FairValue.setDirtyFlag`, and
  `CalculationProvider.fairValueChanged`.
- `find_references` worked well for top-level/public methods when the source location pointed exactly at the
  target symbol. It confirmed that `MarketBook.updateLastQuote(...)` is called by `PolygonMarketProvider.emitNewQuote`
  and other market providers.

## Friction Points

- Cursor's workspace guard blocked reading MCP tool descriptor JSON files under the Cursor MCP metadata directory.
  That made schema discovery impossible from the file system, even though the MCP server itself was callable.
- In Cursor Ask mode, `CallMcpTool` was rejected as non-readonly. This prevented JavaLens use until switching to
  Agent mode, even though the intended calls were read-only analysis calls.
- Method references by fully qualified name did not work as expected. Calls like
  `find_references` with `com.jats2.model.portfolio.model.PortfolioManager.registerContract` returned
  `SYMBOL_NOT_FOUND`. Location-based calls were more reliable.
- `search_symbols` did not find private/local method names such as `registerContract` or fields such as
  `ptReceiver`, even though `analyze_type` listed them. This made it hard to jump from a known member name to
  references without manually supplying a source location.
- Location-based calls are powerful but brittle. If the column lands on the wrong token, `find_references` can
  resolve a nearby type instead of the intended method. One attempt near `PortfolioManager.registerContract(...)`
  resolved `MarketState` because the selected column was not on the method symbol.
- `analyze_method` can fail with `Position is not on a method` when the line/column is near the method declaration
  but not on a recognized method token. Moving the location into the method body often works better.

## Practical Usage Notes

- Prefer this sequence in Cursor:
  1. `health_check`
  2. `list_projects`
  3. `analyze_type` for the main class
  4. Use returned `file` + `line` from `analyze_type`
  5. Call `analyze_method` / `find_references` with `filePath`, `line`, and `column`
- For methods, use a line inside the method body or directly on a method invocation when possible, not just the
  declaration line.
- When a reference call returns surprising results, inspect the resolved `symbol` in the response before trusting
  the reference list.
- For private methods or anonymous-inner-class methods, location-based analysis is currently more reliable than
  name-based lookup.
- JavaLens results should be paired with direct source reads for final analysis. JavaLens is excellent for call
  graph confirmation, but source reads are still needed to understand locking, map implementations, and comments.

## Improvement Requests

- Expose tool schemas through the MCP server itself, or make them discoverable from within the workspace guard.
- Mark read-only JavaLens tools as safe in Cursor Ask mode, especially `health_check`, `list_projects`,
  `analyze_type`, `analyze_method`, `find_references`, `search_symbols`, and diagnostics tools.
- Improve method-symbol lookup by Java FQN, including `Type.method` and fully qualified `package.Type.method`.
- Let `search_symbols` optionally include methods, fields, private members, and anonymous/nested-class members.
- Add a "symbol at location" diagnostic mode that says exactly what JavaLens resolved before doing references.
- For `Position is not on a method`, return nearby candidate methods and their declaration ranges.
- Add examples to each error response showing the accepted argument shape for that tool.

## Bottom Line

JavaLens was valuable for validating the latency investigation. It confirmed the important call chain:

```text
PolygonMarketProvider.emitNewQuote
  -> MarketBook.updateLastQuote
       -> IMarketDataReceiver.processQuote
            -> PortfolioManager.ptReceiver.processQuote
```

The biggest gains would come from smoother schema discovery, read-only tool availability in Ask mode, and more
robust method/member lookup by name.

# Refactoring tool contract (durable policy, effective v1.9.0)

Every tool that **mutates source** MUST implement the apply path. This is a
PR-review gate from Sprint 14b onward — see
[`sprints/sprint-14b-refactoring-full-apply.md`](sprints/sprint-14b-refactoring-full-apply.md)
for the design rationale.

## The contract

1. The tool builds a JDT `Change` (`TextFileChange` / `CompositeChange` for
   text-edit-based refactorings via `ChangeEngine.fromFileEdits`; LTK
   `RefactoringChange` for structural ones; `CreateFileChange` for new files;
   `SourceCommit` for whole-CU codegen rewrites).
2. By default (`auto_apply: true`): the tool performs the change, captures the
   undo-Change, caches it in the shared `RefactoringChangeCache`, and returns
   `{ filesModified, diff, undoChangeId, summary }`.
3. With `auto_apply: false`: the tool caches the un-performed Change and
   returns `{ changeId, diff, summary }`. `apply_refactoring(changeId)`
   commits it later; `inspect_refactoring(changeId)` re-examines the diff.
4. NEVER return raw text-edit collections expecting the agent to hand-apply.
   Sprint 14b eliminated this pattern; new tools must not reintroduce it.

The implementation bases are `AbstractApplyingRefactoringTool` (text-edit
tools — implement `prepareChange`) and `AbstractRefactoringTool` (LTK
descriptor/processor tools — call `runRefactoring(service, …, arguments)`).
Schema decoration via `withAutoApply(withProjectKey(schema))`.

## PR-review checklist

- [ ] Does the new refactoring tool extend `AbstractApplyingRefactoringTool`
      (or `AbstractRefactoringTool` for LTK-based ones), or implement the
      identical contract?
- [ ] Does it return `{ filesModified | changeId, diff, undoChangeId?, summary }`?
- [ ] Does it accept `auto_apply: bool` and honour it?
- [ ] Do its tests include BOTH a happy-path apply-with-on-disk-assertions AND
      an undo round-trip restoring the original content byte-for-byte?
- [ ] Do its tests run against a TEMP COPY of the fixture
      (`TestProjectHelper.loadProjectCopy`), never the checked-in fixture?
- [ ] If the tool is composite (like `replace_duplicates`), do all rewrites
      land in ONE Change so LTK performs them atomically with a single undo
      handle?
- [ ] Is the tool ABSENT from `ToolRegistry`'s read-only sets (mutating tools
      must not carry `readOnlyHint`)?

Tools NOT subject to this policy: `find_*`, `analyze_*`, `get_*`, `search_*` —
anything that returns data without mutating source.

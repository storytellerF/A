
# Add Tail Log Parameter to Dev CLI Plan

## Summary
Add a new parameter to dev/cli that allows users to use `tail` to follow log files for cloud server and worker.

## Repo Research Results
- **Main CLI File**: [CliMain.kt](/home/kx/Projects/A/dev/cli/src/main/kotlin/com/storyteller_f/a/app/dev_cli/CliMain.kt)
- **Log File Paths**:
  - Cloud Server: `${LOG_PATH}/A.log`
  - Cloud Worker: `${LOG_PATH}/A_worker.log`
- **How LOG_PATH is Determined**: [setLogPath()](/home/kx/Projects/A/backend/core/src/main/kotlin/com/storyteller_f/a/backend/core/Env.kt#L69)
  - Defaults to `~/log` (on Unix-like systems) or `%TEMP%/log` (Windows)

## Files to Modify
Only [CliMain.kt](/home/kx/Projects/A/dev/cli/src/main/kotlin/com/storyteller_f/a/app/dev_cli/CliMain.kt)

## Implementation Steps
1. **Add new option to CLI parser**:
   - Add a `--log`/`-l` option that accepts target (s/server, w/worker)
2. **Implement log tailing**:
   - Determine LOG_PATH using the same logic as [setLogPath()](/home/kx/Projects/A/backend/core/src/main/kotlin/com/storyteller_f/a/backend/core/Env.kt#L69)
   - Map target to log file:
     - s/server → `${LOG_PATH}/A.log`
     - w/worker → `${LOG_PATH}/A_worker.log`
   - Use `ProcessBuilder` to execute `tail -f` on the log file
   - Handle OS compatibility (Windows might use different tools, but we'll start with Unix)

## Potential Dependencies
No new dependencies needed. We'll use `ProcessBuilder` from Java standard library.

## Risk Handling
- **Log file doesn't exist**: Handle gracefully and notify user
- **OS compatibility**: Check OS type and handle appropriately (default to Unix-style, maybe print warning on Windows)

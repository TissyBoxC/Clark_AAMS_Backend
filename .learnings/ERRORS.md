# Errors

Command failures and integration errors.

---

## [ERR-20260610-001] gradle_wrapper

**Logged**: 2026-06-10T00:00:00Z
**Priority**: high
**Status**: pending
**Area**: tests

### Summary
`./gradlew test` failed because the wrapper points to a missing local zip file.

### Error
```text
Exception in thread "main" java.io.FileNotFoundException: D:\service\gradle-9.4.1\gradle-9.4.1-all.zip
```

### Context
- Command attempted: `./gradlew test`
- `gradle/wrapper/gradle-wrapper.properties` uses `distributionUrl=file:///D:/service/gradle-9.4.1/gradle-9.4.1-all.zip`
- Local `gradle 9.4.1` is installed and available from `gradle -v`

### Suggested Fix
Use the installed `gradle` binary for verification or restore the local wrapper distribution file.

### Metadata
- Reproducible: yes
- Related Files: gradle/wrapper/gradle-wrapper.properties

---

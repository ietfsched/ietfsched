# Code Coverage for Finding Unused Code

This document explains how to use Android's built-in code coverage tools with regression tests to identify unused methods and classes.

## Overview

Android Gradle Plugin includes built-in code coverage support using JaCoCo. When you run your regression tests with coverage enabled, you can generate reports showing which code paths are executed during testing. Code with 0% coverage may be unused and candidates for removal.

## Setup

Code coverage is already enabled in `app/build.gradle`:

```gradle
buildTypes {
    debug {
        testCoverageEnabled true
    }
}
```

## Generating Coverage Reports

### Quick Method

Run the provided script:

```bash
cd ietfsched
./generate_coverage.sh
```

This will:
1. Run all regression tests with coverage enabled
2. Generate an HTML coverage report
3. Open the report in your browser

### Manual Method

```bash
cd ietfsched

# Run tests with coverage
./gradlew connectedDebugAndroidTest

# Generate HTML report
./gradlew createDebugCoverageReport
```

## Reading Coverage Reports

After running the script, the HTML report will open in your browser. The report shows:

- **Overall coverage percentage** for each package/class
- **Line-by-line coverage** - green (covered), red (not covered), yellow (partially covered)
- **Method coverage** - which methods were called during tests

### Finding Unused Code

1. **Look for 0% coverage classes**: These classes are never instantiated or used during tests
2. **Check method coverage**: Methods with 0% coverage may be unused
3. **Review partially covered classes**: Some methods might be unused even if the class is used

### Important Notes

⚠️ **Coverage ≠ Unused Code**

- **False positives**: Code may be used in production but not exercised by tests
  - Example: Error handlers, edge cases, initialization code
- **False negatives**: Code may be covered by tests but unused in production
  - Example: Test-only code paths, deprecated methods still tested

**Best Practice**: Use coverage reports as a guide, but verify manually:
1. Check if code is called from other parts of the app (grep/search)
2. Review if code handles edge cases or error conditions
3. Check if code is part of a public API that external code might use
4. Consider if code will be needed in the future

## Coverage Report Location

Reports are generated in:
- `app/build/reports/coverage/debug/index.html` (HTML report)
- `app/build/outputs/code_coverage/debugAndroidTest/connected/` (raw coverage data)

## Using Coverage to Clean Up Code

### Workflow

1. **Generate coverage report** after running all regression tests
2. **Identify candidates**: Look for classes/methods with 0% coverage
3. **Verify manually**: 
   - Search codebase for references (`grep -r "ClassName"`)
   - Check if it's part of a public API
   - Review git history to understand why it was added
4. **Remove carefully**: Start with obviously unused code, test thoroughly
5. **Re-run tests**: Ensure nothing breaks after removal

### Example: Finding Unused Methods

```bash
# After generating coverage report, search for specific class usage
cd ietfsched
grep -r "UnusedClassName" app/src/main/java/

# Check if it's referenced in resources
grep -r "unused_class" app/src/main/res/
```

## Limitations

- **Instrumentation tests only**: Coverage only tracks code executed during `connectedAndroidTest`
- **Not production coverage**: Shows what tests exercise, not what users actually use
- **Reflection/Dynamic code**: May not be accurately tracked
- **Native code**: Not covered by Java/Kotlin coverage tools

## Alternative: ProGuard/R8 Analysis

For release builds, ProGuard/R8 already removes unused code. You can analyze what gets removed:

1. Build a release APK with `minifyEnabled true`
2. Check `app/build/outputs/mapping/release/mapping.txt` to see what was removed
3. This shows what R8 considers unused in release builds

## Integration with CI/CD

To generate coverage reports in CI:

```yaml
# Example GitHub Actions step
- name: Generate Coverage Report
  run: |
    cd ietfsched
    ./gradlew connectedDebugAndroidTest createDebugCoverageReport
    # Upload coverage report as artifact
```

## References

- [Android Code Coverage Documentation](https://developer.android.com/studio/test/coverage)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [Android Testing Guide](https://developer.android.com/training/testing)


# server

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:
 * [Ktor Documentation](https://ktor.io/docs/home.html)
 * [Ktor GitHub page](https://github.com/ktorio/ktor)
 * [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).


## Features
Here's a list of features included in this project:

| Name | Description |
|------|-------------|

## Building & Running
To build or run the project, use one of the following tasks:


| Task | Description |
|------|-------------|
| `./gradlew test`    | Run the tests     |
| `./gradlew build`   | Build the project |
| `./gradlew run`     | Run the server    |

If the server starts successfully, you'll see the following output:
```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Test suites

The default server checks are isolated from developer database and provider configuration:

```powershell
.\gradlew test
.\gradlew check
```

Database-backed integration tests require a disposable PostgreSQL database with `test` in its database name. Set only test credentials for these variables; the Gradle task refuses to start without the explicit opt-in and URL:

```powershell
$env:TEST_DATABASE_URL = "postgresql://postgres:test-only-password@localhost:5432/collabsphere_test?sslmode=disable"
$env:COLLABSPHERE_ALLOW_TEST_DB = "YES"
.\gradlew checkWithIntegration
```

CI creates this PostgreSQL service automatically. Never set `TEST_DATABASE_URL` to a production, staging, or personal development database. Integration tests blank provider credentials and disable external-provider schedulers.

Android checks run from `collabsphere_app`:

```powershell
.\gradlew :app:testDebugUnitTest
.\gradlew :app:compileDebugAndroidTestKotlin
```

To execute Compose tests, start an Android emulator or connect a test device, then run:

```powershell
.\gradlew :app:connectedDebugAndroidTest
```

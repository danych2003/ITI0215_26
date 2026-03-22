# Run Nodes

Open PowerShell in the project root and run:

```powershell
Start-Process powershell -ArgumentList '-NoExit', '-Command', '.\gradlew.bat runNode8081' ; Start-Process powershell -ArgumentList '-NoExit', '-Command', '.\gradlew.bat runNode8082' ; Start-Process powershell -ArgumentList '-NoExit', '-Command', '.\gradlew.bat runNode8083'
```

This starts three node processes on:

- `localhost:8081`
- `localhost:8082`
- `localhost:8083`

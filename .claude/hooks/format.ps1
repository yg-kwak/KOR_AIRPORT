# PostToolUse 훅 — 저장 후 자동 포맷 (비차단, 항상 exit 0)
# 프론트엔드 파일은 prettier(있을 때)로 즉시 포맷.
# Java 는 파일 단위 Gradle 실행이 무거우므로 /commit 에서 spotlessApply 로 일괄 처리한다.

$ErrorActionPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }
try { $payload = $raw | ConvertFrom-Json } catch { exit 0 }

$path = $payload.tool_input.file_path
if (-not $path -or -not (Test-Path $path)) { exit 0 }

$ext = ([IO.Path]::GetExtension($path)).ToLower()
$fe  = @('.ts', '.tsx', '.js', '.jsx', '.json', '.css', '.scss', '.md', '.html')

if ($fe -contains $ext) {
    if (Get-Command npx -ErrorAction SilentlyContinue) {
        try { & npx --no-install prettier --write $path | Out-Null } catch { }
    }
}

# Java 파일 수정 시 커밋 전 일괄 포맷이 필요함을 표준출력으로 안내 (비차단)
if ($ext -eq '.java') {
    Write-Output "포맷 안내: Java 파일 변경됨 → 커밋 시 /commit 이 gradlew spotlessApply 를 실행합니다."
}

exit 0

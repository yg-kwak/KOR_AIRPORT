# PreToolUse 훅 — 비밀값 파일 보호 (불변식: "비밀값 금지")
# stdin(JSON)에서 대상 파일 경로를 읽어, .env / 키 / 키스토어 쓰기를 차단한다.
# 차단: exit 2 + stderr 사유 (Claude 컨텍스트에 사유가 주입되어 스스로 우회하도록 유도)

$ErrorActionPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }
try { $payload = $raw | ConvertFrom-Json } catch { exit 0 }

$path = $payload.tool_input.file_path
if (-not $path) { exit 0 }

$name = Split-Path $path -Leaf

# .env / .env.local 등은 차단, 단 .env.example / .env.sample 템플릿은 허용
$isEnv       = ($name -match '(^|[\\/])\.env($|\.)') -and ($name -notmatch '\.(example|sample|template)$')
$isSecretExt = ($name -match '\.(pem|p12|pfx|keystore|jks|key)$')
$isSecretYml = ($name -match '(secret|credential|password).*\.(yml|yaml|properties)$')

if ($isEnv -or $isSecretExt -or $isSecretYml) {
    [Console]::Error.WriteLine("[하네스 차단] 비밀값 파일은 커밋/수정할 수 없습니다: $name")
    [Console]::Error.WriteLine("→ 비밀값은 .env.example 템플릿에 '키 이름만' 남기고, 실제 값은 배포 시크릿 매니저를 사용하세요.")
    [Console]::Error.WriteLine("→ 규칙: docs/security.md")
    exit 2
}

exit 0

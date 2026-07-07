# PreToolUse 훅 — 생성물/빌드 산출물 보호 (불변식: "생성물 수정 금지")
# 빌드·의존성·프레임워크 생성 디렉터리에 대한 쓰기를 차단한다.
# (모바일의 "네이티브 폴더 보호"를 웹/Gradle 스택에 맞게 적용)

$ErrorActionPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }
try { $payload = $raw | ConvertFrom-Json } catch { exit 0 }

$path = $payload.tool_input.file_path
if (-not $path) { exit 0 }

# 경로 구분자 정규화 후 세그먼트 기준으로 매칭
$norm = ($path -replace '\\', '/')

$protected = @(
    'build', '.gradle', 'target', 'out', 'bin',
    'node_modules', 'dist', '.next', 'coverage',
    'generated', 'gen', '.egov'
)

foreach ($dir in $protected) {
    if ($norm -match "(^|/)$([regex]::Escape($dir))(/|$)") {
        [Console]::Error.WriteLine("[하네스 차단] 생성물/빌드 디렉터리는 직접 수정할 수 없습니다: $dir/ 이하")
        [Console]::Error.WriteLine("→ 원본 소스(project/src, web/src 등)를 수정하고 다시 빌드하세요.")
        exit 2
    }
}

exit 0

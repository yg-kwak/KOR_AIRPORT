# SessionEnd 훅 — 세션 종료 요약
# 변경 파일 요약과 다음 단계를 로그로 남긴다. (비차단)

$ErrorActionPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
[void][Console]::In.ReadToEnd()

Write-Output "=== CJAirPort 세션 종료 요약 ==="

$isGit = $false
try { git rev-parse --is-inside-work-tree 2>$null | Out-Null; if ($?) { $isGit = $true } } catch { }

if ($isGit) {
    $status = git status --short 2>$null
    if ($status) {
        Write-Output "변경/미커밋 파일:"
        $status | ForEach-Object { Write-Output "  $_" }
        Write-Output ""
        Write-Output "다음 단계 → /review 로 불변식(AGENTS.md §4) 점검 후 /commit 으로 커밋하세요."
    } else {
        Write-Output "working tree clean — 커밋할 변경 없음."
    }
} else {
    Write-Output "git 저장소가 아직 초기화되지 않았습니다. 'git init' 후 /commit 을 사용하세요."
}

exit 0

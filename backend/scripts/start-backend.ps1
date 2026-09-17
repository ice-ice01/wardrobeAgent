param(
    [ValidateSet('mock','live')]
    [string]$AiMode = 'live',
    [ValidateRange(1,65535)]
    [int]$ServerPort = 8080,
    [string]$AllowedOrigins = '',
    [ValidateSet('MOCK','FASHN_V1_6','GPT_IMAGE_EDIT','SPRING_AI_IMAGE')]
    [string]$TryOnProvider = 'GPT_IMAGE_EDIT',
    [switch]$EnableSpringAiFallback,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'

foreach ($name in @('WARDROBE_DB_USERNAME','WARDROBE_DB_PASSWORD')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($name, 'User')
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
    if ([string]::IsNullOrWhiteSpace($value)) { throw "缺少环境变量 $name" }
}

if ($AiMode -eq 'live') {
    foreach ($name in @('AI_API_KEY','AI_BASE_URL','AI_CHAT_BASE_URL','AI_CHAT_TIMEOUT','AI_MODEL')) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ([string]::IsNullOrWhiteSpace($value)) {
            $value = [Environment]::GetEnvironmentVariable($name, 'User')
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                [Environment]::SetEnvironmentVariable($name, $value, 'Process')
            }
        }
        if ($name -notin @('AI_CHAT_BASE_URL','AI_CHAT_TIMEOUT') -and [string]::IsNullOrWhiteSpace($value)) {
            throw "真实 AI 模式缺少环境变量 $name"
        }
    }
}

foreach ($name in @('AI_EMBEDDING_API_KEY','AI_EMBEDDING_BASE_URL','AI_EMBEDDING_MODEL','AI_EMBEDDING_INDEX_VERSION')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($name, 'User')
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

foreach ($name in @('FASHN_API_KEY','AI_API_KEY','AI_BASE_URL','AI_IMAGE_MODEL')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($name, 'User')
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

if ($TryOnProvider -eq 'FASHN_V1_6') {
    if ([string]::IsNullOrWhiteSpace($env:FASHN_API_KEY)) { throw 'FASHN 试穿缺少环境变量 FASHN_API_KEY' }
    $env:TRYON_REAL_ENABLED = 'true'
}
if ($TryOnProvider -in @('GPT_IMAGE_EDIT','SPRING_AI_IMAGE') -or $EnableSpringAiFallback) {
    foreach ($name in @('AI_API_KEY','AI_BASE_URL')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            throw "AI 图片 Provider 缺少共享环境变量 $name"
        }
    }
}
if ($TryOnProvider -in @('GPT_IMAGE_EDIT','SPRING_AI_IMAGE')) {
    $env:TRYON_REAL_ENABLED = 'true'
}

$env:AI_MODE = $AiMode
$env:SERVER_PORT = $ServerPort
$env:TRYON_PROVIDER = $TryOnProvider
$env:TRYON_SPRING_AI_FALLBACK_ENABLED = $EnableSpringAiFallback.IsPresent.ToString().ToLowerInvariant()
if (-not [string]::IsNullOrWhiteSpace($AllowedOrigins)) { $env:WARDROBE_ALLOWED_ORIGINS = $AllowedOrigins }
if ($Offline) {
    $env:AI_SEMANTIC_RETRIEVAL_ENABLED = 'false'
    $env:AI_MULTIMODAL_ENABLED = 'false'
}
Push-Location (Join-Path $PSScriptRoot '..')
try { & .\mvnw.cmd spring-boot:run } finally { Pop-Location }

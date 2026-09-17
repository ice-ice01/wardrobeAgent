param([string]$MysqlExe = "D:\MySQL\mysql-8.4.11\bin\mysql.exe")

$ErrorActionPreference = "Stop"
$credential = Get-Credential -UserName "root" -Message "输入 MySQL 管理员凭据"
$appUser = [Environment]::GetEnvironmentVariable("WARDROBE_DB_USERNAME", "User")
$appPassword = [Environment]::GetEnvironmentVariable("WARDROBE_DB_PASSWORD", "User")
if ([string]::IsNullOrWhiteSpace($appUser) -or [string]::IsNullOrWhiteSpace($appPassword)) {
    throw "请先设置 WARDROBE_DB_USERNAME 和 WARDROBE_DB_PASSWORD 用户环境变量。"
}
if ($appUser -notmatch "^[A-Za-z0-9_]+$") { throw "数据库用户名只能包含字母、数字和下划线。" }
$escapedPassword = $appPassword.Replace("'", "''")
$sql = @"
CREATE DATABASE IF NOT EXISTS wardrobe_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY '$escapedPassword';
ALTER USER '$appUser'@'localhost' IDENTIFIED BY '$escapedPassword';
GRANT ALL PRIVILEGES ON wardrobe_agent.* TO '$appUser'@'localhost';
FLUSH PRIVILEGES;
"@
$env:MYSQL_PWD = $credential.GetNetworkCredential().Password
try {
    $sql | & $MysqlExe --protocol=TCP --host=127.0.0.1 --port=3306 --user=$($credential.UserName)
    if ($LASTEXITCODE -ne 0) { throw "MySQL 初始化失败，退出码 $LASTEXITCODE。" }
    Write-Host "wardrobe_agent 数据库和应用账号已就绪。"
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Remove-Variable credential,appPassword,escapedPassword,sql -ErrorAction SilentlyContinue
}

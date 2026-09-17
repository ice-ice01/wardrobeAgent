param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\src\main\resources\static\assets\seed'),
    [int]$ScanLimit = 10000,
    [switch]$SkipImages
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
[System.IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null

$dataset = 'PestoRosso/lamoda-fashion-product-images'
$datasetPage = 'https://huggingface.co/datasets/PestoRosso/lamoda-fashion-product-images'
$targets = [ordered]@{
    INNER_TOP = 20
    OUTER_TOP = 15
    BOTTOM = 20
    DRESS = 15
    SHOES = 20
    ACCESSORY = 10
}
$selected = @{}
foreach ($slot in $targets.Keys) { $selected[$slot] = [System.Collections.Generic.List[object]]::new() }
$legacyIds = @(15970,26960,53759,13089,27846,6889,19540,26163,56822,39386,32590,10406,9036,39988,49495,11940,59263,25947)
$seenIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($id in $legacyIds) { [void]$seenIds.Add($id) }

function Resolve-Slot($row) {
    $master = [string]$row.master_category
    $sub = [string]$row.sub_category
    $article = [string]$row.article_type
    if ($master -eq 'Footwear') { return 'SHOES' }
    if ($master -eq 'Accessories') { return 'ACCESSORY' }
    if ($master -ne 'Apparel') { return $null }
    if ($sub -eq 'Dress' -or $article -match 'Dress') { return 'DRESS' }
    if ($sub -eq 'Bottomwear') { return 'BOTTOM' }
    if ($sub -ne 'Topwear') { return $null }
    if ($article -match 'Blazer|Jacket|Sweater|Sweatshirt|Coat|Shrug|Waistcoat') { return 'OUTER_TOP' }
    return 'INNER_TOP'
}

function Is-Complete {
    foreach ($slot in $targets.Keys) {
        if ($selected[$slot].Count -lt $targets[$slot]) { return $false }
    }
    return $true
}

for ($offset = 0; $offset -lt $ScanLimit -and -not (Is-Complete); $offset += 100) {
    $encodedDataset = [Uri]::EscapeDataString($dataset)
    $uri = "https://datasets-server.huggingface.co/rows?dataset=$encodedDataset&config=default&split=train&offset=$offset&length=100"
    Write-Host "Scanning catalog rows $offset-$($offset + 99)..."
    $response = Invoke-RestMethod -Uri $uri
    foreach ($result in $response.rows) {
        $row = $result.row
        $id = [int]$row.id
        if (-not $seenIds.Add($id)) { continue }
        $slot = Resolve-Slot $row
        if ($null -eq $slot -or $selected[$slot].Count -ge $targets[$slot]) { continue }
        if ($null -eq $row.image -or [string]::IsNullOrWhiteSpace([string]$row.image.src)) { continue }
        $selected[$slot].Add($row)
    }
}

if (-not (Is-Complete)) {
    $missing = foreach ($slot in $targets.Keys) {
        if ($selected[$slot].Count -lt $targets[$slot]) { "$slot=$($selected[$slot].Count)/$($targets[$slot])" }
    }
    throw "Catalog quotas not met before ScanLimit=${ScanLimit}: $($missing -join ', ')"
}

function Season-Tags([string]$season) {
    switch ($season) {
        'Spring' { return @('春') }
        'Summer' { return @('夏') }
        'Fall' { return @('秋') }
        'Winter' { return @('冬') }
        default { return @('四季') }
    }
}

function Scene-Tags([string]$usage) {
    switch -Regex ($usage) {
        'Formal' { return @('通勤','面试') }
        'Sport' { return @('运动','休闲') }
        'Ethnic' { return @('约会','旅行') }
        default { return @('休闲','旅行') }
    }
}

function Style-Tags([string]$usage, [string]$article) {
    if ($usage -match 'Formal') { return @('正式','简约') }
    if ($usage -match 'Sport') { return @('运动','休闲') }
    if ($article -match 'Jeans|Shirt|Dress') { return @('休闲','经典') }
    return @('休闲','简约')
}

function Category-Name([string]$slot) {
    switch ($slot) {
        'INNER_TOP' { return '上装' }
        'OUTER_TOP' { return '外套' }
        'BOTTOM' { return '下装' }
        'DRESS' { return '连衣裙' }
        'SHOES' { return '鞋' }
        'ACCESSORY' { return '配饰' }
    }
}

function Warmth-Level([string]$slot) {
    switch ($slot) { 'OUTER_TOP' { 4 } 'BOTTOM' { 2 } 'DRESS' { 2 } default { 1 } }
}

function Breathability-Level([string]$slot) {
    switch ($slot) { 'OUTER_TOP' { 2 } 'BOTTOM' { 3 } 'SHOES' { 3 } default { 4 } }
}

$jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$encoderParameters = New-Object System.Drawing.Imaging.EncoderParameters 1
$encoderParameters.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), ([long]90)
$manifest = @()
$index = 0

try {
    foreach ($slot in $targets.Keys) {
        foreach ($row in $selected[$slot]) {
            $index++
            $filename = 'catalog-{0:D3}.jpg' -f $index
            $localPath = "/assets/seed/$filename"
            if (-not $SkipImages) {
                $tempFile = [System.IO.Path]::GetTempFileName()
                try {
                    Write-Host "Downloading $filename`: $($row.product_display_name)"
                    Invoke-WebRequest -UseBasicParsing -Uri $row.image.src -OutFile $tempFile
                    $source = [System.Drawing.Image]::FromFile($tempFile)
                    try {
                        $canvas = New-Object System.Drawing.Bitmap 720, 900
                        $graphics = [System.Drawing.Graphics]::FromImage($canvas)
                        try {
                            $graphics.Clear([System.Drawing.Color]::White)
                            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                            $scale = [Math]::Min(656.0 / $source.Width, 836.0 / $source.Height)
                            $width = [int][Math]::Round($source.Width * $scale)
                            $height = [int][Math]::Round($source.Height * $scale)
                            $x = [int][Math]::Round((720 - $width) / 2.0)
                            $y = [int][Math]::Round((900 - $height) / 2.0)
                            $graphics.DrawImage($source, $x, $y, $width, $height)
                            $canvas.Save((Join-Path $OutputDirectory $filename), $jpegCodec, $encoderParameters)
                        } finally {
                            $graphics.Dispose()
                            $canvas.Dispose()
                        }
                    } finally {
                        $source.Dispose()
                    }
                } finally {
                    Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue
                }
            }

            $manifest += [PSCustomObject]@{
                index = $index
                sourceItemId = [string]$row.id
                name = [string]$row.product_display_name
                category = Category-Name $slot
                slot = $slot
                color = if ([string]::IsNullOrWhiteSpace([string]$row.base_color)) { 'UNKNOWN' } else { [string]$row.base_color }
                material = 'UNKNOWN'
                seasonTags = @(Season-Tags ([string]$row.season))
                sceneTags = @(Scene-Tags ([string]$row.usage))
                styleTags = @(Style-Tags ([string]$row.usage) ([string]$row.article_type))
                warmthLevel = Warmth-Level $slot
                breathabilityLevel = Breathability-Level $slot
                localPath = $localPath
                sourceDataset = $dataset
                sourcePage = $datasetPage
                license = 'MIT'
            }
        }
    }

    $manifestPath = Join-Path $OutputDirectory 'public-catalog-manifest.json'
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8
} finally {
    $encoderParameters.Dispose()
}

Write-Host "Prepared $($manifest.Count) public catalog items in $OutputDirectory"
foreach ($slot in $targets.Keys) { Write-Host "$slot=$($selected[$slot].Count)" }

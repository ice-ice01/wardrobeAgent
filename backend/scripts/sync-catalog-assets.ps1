param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\src\main\resources\static\assets\seed'),
    [int]$ScanLimit = 1200
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
[System.IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null

$dataset = 'PestoRosso/lamoda-fashion-product-images'
$datasetPage = 'https://huggingface.co/datasets/PestoRosso/lamoda-fashion-product-images'
$catalog = @(
    @{ Index=1; Id=15970 }, @{ Index=2; Id=26960 }, @{ Index=3; Id=53759 }, @{ Index=4; Id=13089 },
    @{ Index=5; Id=27846 }, @{ Index=6; Id=6889 }, @{ Index=7; Id=19540 }, @{ Index=8; Id=26163 },
    @{ Index=9; Id=56822 }, @{ Index=10; Id=39386 }, @{ Index=11; Id=32590 }, @{ Index=12; Id=10406 },
    @{ Index=13; Id=9036 }, @{ Index=14; Id=39988 }, @{ Index=15; Id=49495 }, @{ Index=16; Id=11940 },
    @{ Index=17; Id=59263 }, @{ Index=18; Id=25947 }
)

$wanted = @{}
foreach ($entry in $catalog) { $wanted[[int]$entry.Id] = $entry }
$found = @{}

for ($offset = 0; $offset -lt $ScanLimit -and $found.Count -lt $wanted.Count; $offset += 100) {
    $encodedDataset = [Uri]::EscapeDataString($dataset)
    $uri = "https://datasets-server.huggingface.co/rows?dataset=$encodedDataset&config=default&split=train&offset=$offset&length=100"
    Write-Host "Reading catalog rows $offset-$($offset + 99)..."
    $response = Invoke-RestMethod -Uri $uri
    foreach ($result in $response.rows) {
        $id = [int]$result.row.id
        if ($wanted.ContainsKey($id)) { $found[$id] = $result.row }
    }
}

$missing = @($wanted.Keys | Where-Object { -not $found.ContainsKey($_) })
if ($missing.Count -gt 0) {
    throw "Catalog rows not found before ScanLimit=${ScanLimit}: $($missing -join ', ')"
}

$jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$encoderParameters = New-Object System.Drawing.Imaging.EncoderParameters 1
$encoderParameters.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), ([long]90)
$manifest = @()

foreach ($entry in $catalog) {
    $row = $found[[int]$entry.Id]
    $tempFile = [System.IO.Path]::GetTempFileName()
    try {
        Write-Host "Downloading item-$('{0:D2}' -f $entry.Index): $($row.product_display_name)"
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
                $outputPath = Join-Path $OutputDirectory ('item-{0:D2}.jpg' -f $entry.Index)
                $canvas.Save($outputPath, $jpegCodec, $encoderParameters)
            } finally {
                $graphics.Dispose()
                $canvas.Dispose()
            }
            $manifest += [PSCustomObject]@{
                index = $entry.Index
                datasetId = [int]$row.id
                productDisplayName = $row.product_display_name
                gender = $row.gender
                masterCategory = $row.master_category
                subCategory = $row.sub_category
                articleType = $row.article_type
                baseColor = $row.base_color
                season = $row.season
                usage = $row.usage
                sourceWidth = $source.Width
                sourceHeight = $source.Height
                localPath = ('/assets/seed/item-{0:D2}.jpg' -f $entry.Index)
                sourceDataset = $dataset
                sourcePage = $datasetPage
                license = 'MIT'
            }
        } finally {
            $source.Dispose()
        }
    } finally {
        Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue
    }
}

$manifestPath = Join-Path $OutputDirectory 'catalog-manifest.json'
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding utf8
$encoderParameters.Dispose()
Write-Host "Downloaded and normalized $($manifest.Count) catalog assets into $OutputDirectory"

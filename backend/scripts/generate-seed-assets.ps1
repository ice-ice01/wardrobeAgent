param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\src\main\resources\static\assets\seed')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
[System.IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null

$items = @(
    @{ Kind='top'; Color='#E9E1CF'; Accent='#BBB09A' }, @{ Kind='top'; Color='#789BAD'; Accent='#557786' },
    @{ Kind='top'; Color='#20211F'; Accent='#545650' }, @{ Kind='sweat'; Color='#8C908D'; Accent='#646965' },
    @{ Kind='jacket'; Color='#1E2222'; Accent='#B8B3A8' }, @{ Kind='jacket'; Color='#B99B6D'; Accent='#DED0B8' },
    @{ Kind='cardigan'; Color='#32495C'; Accent='#B2A98E' }, @{ Kind='pants'; Color='#4D5152'; Accent='#858A89' },
    @{ Kind='pants'; Color='#D7D0BF'; Accent='#AFA997' }, @{ Kind='pants'; Color='#7694AA'; Accent='#B7C8D3' },
    @{ Kind='shorts'; Color='#C4AB83'; Accent='#EEE2CE' }, @{ Kind='dress'; Color='#345B4A'; Accent='#A7B9A2' },
    @{ Kind='shoe'; Color='#283D52'; Accent='#A18765' }, @{ Kind='sneaker'; Color='#ECEDE8'; Accent='#9EA49F' },
    @{ Kind='shoe'; Color='#704C38'; Accent='#B0886D' }, @{ Kind='sandal'; Color='#AF8059'; Accent='#D1B18D' },
    @{ Kind='watch'; Color='#AAB0AF'; Accent='#383B3A' }, @{ Kind='scarf'; Color='#8B3440'; Accent='#D49A91' }
)

function New-Canvas([string]$background) {
    $bitmap = New-Object System.Drawing.Bitmap 720,900
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml($background))
    return @($bitmap,$graphics)
}

function New-Brush([string]$color) { return New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($color)) }
function New-Pen([string]$color,[float]$width) { return New-Object System.Drawing.Pen ([System.Drawing.ColorTranslator]::FromHtml($color)),$width }
function Points([object[]]$coords) { return [System.Drawing.Point[]]($coords | ForEach-Object { New-Object System.Drawing.Point $_[0],$_[1] }) }

function Draw-Garment($graphics,$item,[int]$index) {
    $shadow = New-Brush '#D7D9D2'; $main = New-Brush $item.Color; $accent = New-Brush $item.Accent
    $graphics.FillEllipse($shadow,145,720,430,52)
    switch ($item.Kind) {
        'top' {
            $graphics.FillPolygon($main,(Points @((225,205),(130,280),(177,380),(226,344),(226,690),(494,690),(494,344),(543,380),(590,280),(495,205),(424,180),(296,180))))
            $graphics.FillEllipse((New-Brush '#F3F2EC'),309,170,102,70); $graphics.FillRectangle($accent,348,240,24,8)
        }
        'sweat' {
            $graphics.FillPolygon($main,(Points @((236,195),(136,286),(181,408),(232,365),(221,695),(499,695),(488,365),(539,408),(584,286),(484,195))))
            $graphics.FillEllipse((New-Brush '#F3F2EC'),307,170,106,76); $graphics.FillRectangle($accent,230,636,260,42)
        }
        'jacket' {
            $graphics.FillPolygon($main,(Points @((236,180),(143,277),(184,429),(230,377),(208,710),(512,710),(490,377),(536,429),(577,277),(484,180))))
            $graphics.FillPolygon($accent,(Points @((278,188),(351,305),(304,405),(236,197))))
            $graphics.FillPolygon($accent,(Points @((442,188),(369,305),(416,405),(484,197))))
            $graphics.DrawLine((New-Pen '#D6D2C9' 3),360,294,360,675); $graphics.FillEllipse($accent,350,420,20,20)
        }
        'cardigan' {
            $graphics.FillPolygon($main,(Points @((232,194),(142,286),(181,415),(228,370),(218,700),(502,700),(492,370),(539,415),(578,286),(488,194))))
            $graphics.FillPolygon((New-Brush '#EDEBE4'),(Points @((326,190),(360,286),(394,190),(394,700),(326,700))))
            330..620 | Where-Object { $_ % 58 -eq 40 } | ForEach-Object { $graphics.FillEllipse($accent,350,$_,18,18) }
        }
        'pants' {
            $graphics.FillPolygon($main,(Points @((238,170),(482,170),(500,724),(400,724),(360,392),(320,724),(220,724))))
            $graphics.DrawLine((New-Pen $item.Accent 4),360,190,360,392); $graphics.FillRectangle($accent,238,170,244,24)
        }
        'shorts' {
            $graphics.FillPolygon($main,(Points @((220,238),(500,238),(480,568),(380,560),(360,392),(340,560),(240,568))))
            $graphics.FillRectangle($accent,220,238,280,28); $graphics.DrawLine((New-Pen $item.Accent 4),360,264,360,392)
        }
        'dress' {
            $graphics.FillPolygon($main,(Points @((300,170),(420,170),(458,328),(550,710),(170,710),(262,328))))
            $graphics.FillEllipse((New-Brush '#F3F2EC'),319,146,82,62); $graphics.FillRectangle($accent,276,330,168,24)
            $graphics.DrawLine((New-Pen $item.Accent 3),262,328,184,686); $graphics.DrawLine((New-Pen $item.Accent 3),458,328,536,686)
        }
        'shoe' {
            $graphics.RotateTransform(-7); $graphics.FillPolygon($main,(Points @((160,450),(250,380),(420,430),(546,532),(520,590),(238,590),(142,548))))
            $graphics.FillRectangle($accent,174,562,350,34); $graphics.DrawLine((New-Pen $item.Accent 6),288,438,393,470); $graphics.ResetTransform()
        }
        'sneaker' {
            $graphics.RotateTransform(-6); $graphics.FillPolygon($main,(Points @((142,475),(234,376),(353,427),(551,526),(540,598),(180,598),(118,552))))
            $graphics.FillRectangle($accent,148,568,392,34); 260..410 | Where-Object { $_ % 38 -eq 32 } | ForEach-Object { $graphics.DrawLine((New-Pen '#7A807C' 4),$_,430,$_+48,482) }; $graphics.ResetTransform()
        }
        'sandal' {
            $graphics.RotateTransform(-8); $graphics.FillEllipse($main,190,352,300,390); $graphics.FillRectangle($accent,212,470,258,55); $graphics.FillRectangle($accent,232,610,220,42); $graphics.ResetTransform()
        }
        'watch' {
            $graphics.FillRectangle($main,328,180,64,540); $graphics.FillEllipse($accent,245,320,230,230); $graphics.FillEllipse((New-Brush '#E5E7E3'),263,338,194,194)
            $graphics.DrawLine((New-Pen '#303332' 7),360,435,360,372); $graphics.DrawLine((New-Pen '#303332' 5),360,435,410,465)
        }
        'scarf' {
            $graphics.RotateTransform(-12); $graphics.FillPolygon($main,(Points @((208,168),(482,196),(452,700),(176,672))))
            $graphics.DrawLine((New-Pen $item.Accent 18),215,220,462,245); $graphics.DrawLine((New-Pen $item.Accent 10),190,600,438,628); $graphics.ResetTransform()
        }
    }
    $labelBrush = New-Brush '#777B74'; $font = [System.Drawing.Font]::new('Arial',18,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawString(('WARDROBE {0:D2}' -f $index),$font,$labelBrush,28,838)
    $font.Dispose(); $shadow.Dispose(); $main.Dispose(); $accent.Dispose()
}

for ($index = 1; $index -le $items.Count; $index++) {
    $backgrounds = @('#F1F0EA','#E9EEE9','#F2EDEA','#E8ECEC')
    $canvas = New-Canvas $backgrounds[($index - 1) % $backgrounds.Count]; $bitmap=$canvas[0]; $graphics=$canvas[1]
    Draw-Garment $graphics $items[$index - 1] $index
    $path = Join-Path $OutputDirectory ('item-{0:D2}.png' -f $index); $bitmap.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose()
}

function Draw-Model([int]$index,[string]$name,[string]$top,[string]$bottom) {
    $canvas = New-Canvas @('#E7ECE8','#EDE9E5','#E5EAEC')[$index-1]; $bitmap=$canvas[0]; $graphics=$canvas[1]
    $skin=New-Brush '#C88F70'; $hair=New-Brush @('#292725','#5A4036','#252A2D')[$index-1]; $shirt=New-Brush $top; $trousers=New-Brush $bottom
    $graphics.FillEllipse((New-Brush '#CDD5CF'),130,760,460,55); $graphics.FillEllipse($hair,275,105,170,190); $graphics.FillEllipse($skin,298,132,124,166)
    $graphics.FillPolygon($shirt,(Points @((278,288),(442,288),(505,500),(435,548),(410,756),(310,756),(285,548),(215,500))))
    $graphics.FillPolygon($trousers,(Points @((308,522),(412,522),(455,806),(380,806),(360,590),(340,806),(265,806))))
    $graphics.FillRectangle($skin,222,448,45,220); $graphics.FillRectangle($skin,453,448,45,220)
    $graphics.FillEllipse((New-Brush '#F7F7F3'),254,790,98,35); $graphics.FillEllipse((New-Brush '#F7F7F3'),368,790,98,35)
    $font=[System.Drawing.Font]::new('Arial',18,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel); $graphics.DrawString($name,$font,(New-Brush '#636861'),28,838)
    $font.Dispose(); $skin.Dispose(); $hair.Dispose(); $shirt.Dispose(); $trousers.Dispose(); $graphics.Dispose()
    $bitmap.Save((Join-Path $OutputDirectory ('model-{0:D2}.png' -f $index)),[System.Drawing.Imaging.ImageFormat]::Png); $bitmap.Dispose()
}
Draw-Model 1 'DAILY' '#E9E1CF' '#5B6161'; Draw-Model 2 'COMMUTE' '#263F37' '#343839'; Draw-Model 3 'CASUAL' '#789BAD' '#D5CDBD'

$canvas = New-Canvas '#E2E8E3'; $bitmap=$canvas[0]; $graphics=$canvas[1]
$graphics.FillRectangle((New-Brush '#D7DDD8'),72,72,576,756); $graphics.FillEllipse((New-Brush '#C88F70'),298,108,124,166); $graphics.FillEllipse((New-Brush '#292725'),278,82,164,118)
$graphics.FillPolygon((New-Brush '#E9E1CF'),(Points @((278,270),(442,270),(490,486),(425,520),(410,760),(310,760),(295,520),(230,486))))
$graphics.FillPolygon((New-Brush '#4D5152'),(Points @((304,492),(416,492),(452,792),(379,792),(360,580),(341,792),(268,792))))
$graphics.FillPolygon((New-Brush '#1E2222'),(Points @((250,270),(190,360),(232,555),(286,510),(278,270),(442,270),(434,510),(488,555),(530,360),(470,270))))
$graphics.DrawLine((New-Pen '#B8B3A8' 4),360,312,360,530); $graphics.FillEllipse((New-Brush '#283D52'),245,776,116,42); $graphics.FillEllipse((New-Brush '#283D52'),365,776,116,42)
$font=[System.Drawing.Font]::new('Arial',18,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel); $graphics.DrawString('MOCK TRY-ON RESULT',$font,(New-Brush '#596159'),96,790)
$font.Dispose(); $graphics.Dispose(); $bitmap.Save((Join-Path $OutputDirectory 'tryon-result.png'),[System.Drawing.Imaging.ImageFormat]::Png); $bitmap.Dispose()

Write-Host "Generated 22 seed assets in $OutputDirectory"

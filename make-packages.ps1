param(
  [switch]$Clean
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$pkgs = Join-Path $root "packages"

if (!(Test-Path $pkgs)) {
  throw "Folder not found: $pkgs"
}

function Write-MinJpeg([string]$path) {
  # Минимальный JPEG: FF D8 ... FF D9 (достаточно для “файл есть”)
  [byte[]]$bytes = 0xFF,0xD8,0xFF,0xD9
  [System.IO.File]::WriteAllBytes($path, $bytes)
}

Get-ChildItem -Path $pkgs -Directory | ForEach-Object {
  $dir = $_.FullName
  $name = $_.Name

  $manifest = Join-Path $dir "manifest.xml"
  $dtd = Join-Path $dir "manifest.dtd"

  if (!(Test-Path $manifest)) { throw "manifest.xml not found in $name" }
  if (!(Test-Path $dtd)) { throw "manifest.dtd not found in $name" }

  # создаём фотки, если нет (01/02/03 — 4 штуки, 04 — 2 штуки, 05 — 1 штука)
  $jpgs = Get-ChildItem -Path $dir -Filter "*.jpg" -ErrorAction SilentlyContinue
  if ($jpgs.Count -eq 0) {
    $count = 4
    if ($name -eq "04-invalid-xsd") { $count = 2 }
    if ($name -eq "05-invalid-dtd") { $count = 1 }

    1..$count | ForEach-Object {
      $idx = $_.ToString("0000")
      $p = Join-Path $dir ("photo-$idx.jpg")
      Write-MinJpeg $p
    }
  }

  $zip = Join-Path $dir "package.zip"
  $sha = Join-Path $dir "package.sha256"

  if ($Clean) {
    if (Test-Path $zip) { Remove-Item $zip -Force }
    if (Test-Path $sha) { Remove-Item $sha -Force }
  }

  # Собираем ZIP (manifest + dtd + jpg)
  if (Test-Path $zip) { Remove-Item $zip -Force }

  $items = @($manifest, $dtd) + (Get-ChildItem -Path $dir -Filter "*.jpg" | Select-Object -ExpandProperty FullName)

  Compress-Archive -Path $items -DestinationPath $zip -Force

  $h = Get-FileHash -Path $zip -Algorithm SHA256
  $h.Hash | Out-File -FilePath $sha -Encoding ascii -Force

  Write-Host ("{0}: OK zip={1} sha256={2}" -f $name, $zip, $h.Hash)
}

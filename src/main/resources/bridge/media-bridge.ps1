# Drift media bridge: reads one command per line on stdin, writes one JSON line per reply on stdout.
# Uses the documented Windows.Media.Control (System Media Transport Controls) API. Read/control only.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) {
    $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    if (-not $t.Wait(5000)) { throw 'timed out waiting for Windows media API' }
    $t.Result
}
function Reply($obj) { [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress -Depth 6)); [Console]::Out.Flush() }

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$mgr = Await ($managerType::RequestAsync()) $managerType
Reply @{ ready = $true }

function Find-Session($id) { $mgr.GetSessions() | Where-Object { $_.SourceAppUserModelId -eq $id } | Select-Object -First 1 }

while (($line = [Console]::In.ReadLine()) -ne $null) {
    try {
        $parts = $line.Split(' ', 3)
        switch ($parts[0]) {
            'LIST' {
                $list = @()
                foreach ($s in $mgr.GetSessions()) {
                    $p = Await ($s.TryGetMediaPropertiesAsync()) $propsType
                    $pb = $s.GetPlaybackInfo()
                    $tl = $s.GetTimelineProperties()
                    $list += @{
                        id = $s.SourceAppUserModelId; app = $s.SourceAppUserModelId
                        title = $p.Title; artist = $p.Artist; album = $p.AlbumTitle
                        status = $pb.PlaybackStatus.ToString().ToUpper()
                        position = $tl.Position.TotalSeconds; duration = $tl.EndTime.TotalSeconds
                        canPlayPause = ($pb.Controls.IsPlayEnabled -or $pb.Controls.IsPauseEnabled -or $pb.Controls.IsPlayPauseToggleEnabled)
                        canNext = $pb.Controls.IsNextEnabled; canPrevious = $pb.Controls.IsPreviousEnabled
                        hasArtwork = ($null -ne $p.Thumbnail)
                    }
                }
                $cur = $mgr.GetCurrentSession()
                Reply @{ sessions = $list; current = $(if ($cur) { $cur.SourceAppUserModelId } else { $null }) }
            }
            'ART' {
                $s = Find-Session $parts[1]
                if (-not $s) { Reply @{ data = '' }; continue }
                $p = Await ($s.TryGetMediaPropertiesAsync()) $propsType
                if (-not $p.Thumbnail) { Reply @{ data = '' }; continue }
                $stream = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                if ($stream.Size -gt 2000000) { Reply @{ data = '' }; continue }
                $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
                $ms = New-Object System.IO.MemoryStream
                $net.CopyTo($ms)
                Reply @{ data = [Convert]::ToBase64String($ms.ToArray()) }
            }
            'CTRL' {
                $rest = $parts[2]
                $s = Find-Session $rest
                if (-not $s) { Reply @{ ok = $false }; continue }
                $op = switch ($parts[1]) {
                    'PLAY' { $s.TryPlayAsync() }
                    'PAUSE' { $s.TryPauseAsync() }
                    'TOGGLE' { $s.TryTogglePlayPauseAsync() }
                    'NEXT' { $s.TrySkipNextAsync() }
                    'PREVIOUS' { $s.TrySkipPreviousAsync() }
                    default { throw "unknown control $($parts[1])" }
                }
                Reply @{ ok = [bool](Await $op ([bool])) }
            }
            default { Reply @{ error = "unknown command" } }
        }
    } catch {
        Reply @{ error = $_.Exception.Message }
    }
}

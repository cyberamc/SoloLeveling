$outputFolder = "app\src\main\res"
$downloadsFolder = "$env:USERPROFILE\Downloads"

Write-Host "Copying Saiyan launcher icons..." -ForegroundColor Green

@("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi", "drawable", "mipmap-anydpi-v26") | ForEach-Object {
    $dir = "$outputFolder\$_"
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
}

Copy-Item "$downloadsFolder\ic_launcher-mdpi.png" "$outputFolder\mipmap-mdpi\ic_launcher.png" -Force
Copy-Item "$downloadsFolder\ic_launcher-hdpi.png" "$outputFolder\mipmap-hdpi\ic_launcher.png" -Force
Copy-Item "$downloadsFolder\ic_launcher-xhdpi.png" "$outputFolder\mipmap-xhdpi\ic_launcher.png" -Force
Copy-Item "$downloadsFolder\ic_launcher-xxhdpi.png" "$outputFolder\mipmap-xxhdpi\ic_launcher.png" -Force
Copy-Item "$downloadsFolder\ic_launcher-xxxhdpi.png" "$outputFolder\mipmap-xxxhdpi\ic_launcher.png" -Force

Write-Host "Copying foreground..." -ForegroundColor Green
Copy-Item "$downloadsFolder\ic_launcher_foreground-mdpi.png" "$outputFolder\mipmap-mdpi\ic_launcher_foreground.png" -Force
Copy-Item "$downloadsFolder\ic_launcher_foreground-hdpi.png" "$outputFolder\mipmap-hdpi\ic_launcher_foreground.png" -Force
Copy-Item "$downloadsFolder\ic_launcher_foreground-xhdpi.png" "$outputFolder\mipmap-xhdpi\ic_launcher_foreground.png" -Force
Copy-Item "$downloadsFolder\ic_launcher_foreground-xxhdpi.png" "$outputFolder\mipmap-xxhdpi\ic_launcher_foreground.png" -Force
Copy-Item "$downloadsFolder\ic_launcher_foreground-xxxhdpi.png" "$outputFolder\mipmap-xxxhdpi\ic_launcher_foreground.png" -Force

Write-Host "Copying XML..." -ForegroundColor Green
Copy-Item "$downloadsFolder\ic_launcher_background.xml" "$outputFolder\drawable\ic_launcher_background.xml" -Force
Copy-Item "$downloadsFolder\ic_launcher.xml" "$outputFolder\mipmap-anydpi-v26\ic_launcher.xml" -Force
Copy-Item "$downloadsFolder\ic_launcher_round.xml" "$outputFolder\mipmap-anydpi-v26\ic_launcher_round.xml" -Force

Write-Host "Done! Run: ./gradlew clean build installDebug" -ForegroundColor Green

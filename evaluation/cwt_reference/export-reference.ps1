# Regenerate evaluation/reference/cwt_java_*.csv from the Java MorletCwt.
# This tool depends on the `analytics` module but lives under evaluation/ (it is evaluation
# tooling, not part of the production build). It assembles the analytics classpath via Maven,
# then compiles and runs CwtReferenceExport.java.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# --- JDK 17 (Flink/analytics target) ---
if ($env:JAVA17_HOME -and (Test-Path (Join-Path $env:JAVA17_HOME 'bin\javac.exe'))) {
    $jdk = $env:JAVA17_HOME
} else {
    $jdk = Join-Path $env:USERPROFILE 'scoop\apps\temurin17-jdk\current'
}
if (-not (Test-Path (Join-Path $jdk 'bin\javac.exe'))) { throw "JDK 17 not found. Set JAVA17_HOME." }
$env:JAVA_HOME = $jdk

# --- Maven (PATH, else scoop) ---
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) { $mvn = Join-Path $env:USERPROFILE 'scoop\apps\maven\current\bin\mvn.cmd' }

# --- compile analytics + resolve its dependency classpath ---
& $mvn -q -ntp -f "$root\pom.xml" -pl analytics -am compile
$cpFile = Join-Path $env:TEMP 'circadian-analytics-cp.txt'
& $mvn -q -ntp -f "$root\pom.xml" -pl analytics dependency:build-classpath "-Dmdep.outputFile=$cpFile" | Out-Null
$cp = "$root\analytics\target\classes;$((Get-Content $cpFile -Raw).Trim())"

# --- compile + run the exporter ---
$out = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force $out | Out-Null
& "$jdk\bin\javac.exe" -cp $cp -d $out (Join-Path $PSScriptRoot 'CwtReferenceExport.java')
& "$jdk\bin\java.exe" -cp "$out;$cp" CwtReferenceExport (Join-Path $root 'evaluation\reference')

<#
.SYNOPSIS
Allows Companion connections to the selected development Java runtime on a Private LAN.
.DESCRIPTION
Creates or updates only KNet's named development rule. Access is limited to TCP ports 8181-8183 from the
local subnet on Private networks. Run in an Administrator PowerShell; -WhatIf supports a non-admin preview.
.PARAMETER JavaHome
JDK directory used to run KNet, containing bin/java.exe. Firewall permissions follow this executable path.
.EXAMPLE
.\Enable-CompanionFirewall.ps1 -JavaHome "$env:USERPROFILE\.jdks\jbr-21.0.11" -WhatIf
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param([Parameter(Mandatory = $true)][string]$JavaHome)

$ErrorActionPreference = 'Stop'
$runtimeExecutable = (Resolve-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe')).Path
$ruleSettings = @{
    Name = 'KNet-Development-Companion'
    Enabled = 'True'
    Direction = 'Inbound'
    Action = 'Allow'
    Profile = 'Private'
    Program = $runtimeExecutable
    Protocol = 'TCP'
    LocalPort = @(8181, 8182, 8183)
    RemoteAddress = 'LocalSubnet'
}

if ($PSCmdlet.ShouldProcess($runtimeExecutable, 'Allow Companion TCP 8181-8183 from the local Private subnet')) {
    $principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run this script from an Administrator PowerShell to update Windows Firewall.'
    }
    if (Get-NetFirewallRule -Name $ruleSettings.Name -ErrorAction SilentlyContinue) {
        Set-NetFirewallRule @ruleSettings
    } else {
        New-NetFirewallRule @ruleSettings -DisplayName 'KNet development companion'
    }
}

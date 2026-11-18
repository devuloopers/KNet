# Development desktop Companion access

Windows Firewall grants access per executable and network profile. Installed KNet and development Java have
different permissions. Changing the development JDK also changes the executable that needs access.

Find the Java runtime actually used by KNet, then preview the narrow Private-LAN rule:

```powershell
.\tools\development\Enable-CompanionFirewall.ps1 -JavaHome "$env:USERPROFILE\.jdks\jbr-21.0.11" -WhatIf
```

Run the same command without `-WhatIf` from an Administrator PowerShell. It creates or updates only the named
`KNet-Development-Companion` rule, allowing inbound TCP 8181, 8182 and 8183 from `LocalSubnet` on `Private` networks.
It does not change installed KNet rules or open Public-network access. Reconnect Companion afterward.

A VPN switch can remain enabled on Android while its authenticated desktop control channel is disconnected.
Desktop Online requires that channel and fresh application heartbeats. Capture running/stopped and saved
historical rows are separate state; neither determines device presence or whether Clear can run.

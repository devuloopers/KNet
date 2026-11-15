/** Public destinations live here; download actions use the project's GitHub Releases page. */
export const site = {
  name: "KNet",
  repository: "https://github.com/devuloopers/KNet",
  docs: "https://github.com/devuloopers/KNet/blob/master/docs/README.md",
  releases: "https://github.com/devuloopers/KNet/releases",
  downloadLabel: "Download on GitHub",
  tagline: "Network Inspector & API Studio",
} as const;

/** Three deliberately short capability labels from the selected homepage design. */
export const features = [
  { icon: "devices", label: "Multiple devices" },
  { icon: "conditions", label: "Network conditions" },
  { icon: "intercept", label: "Live interception" },
] as const;

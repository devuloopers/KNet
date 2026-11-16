/** Public destinations; every download goes to the project's GitHub Releases page. */
export const site = {
  name: "KNet",
  repository: "https://github.com/devuloopers/KNet",
  docs: "https://github.com/devuloopers/KNet/blob/master/docs/README.md",
  releases: "https://github.com/devuloopers/KNet/releases",
  downloadLabel: "Download on GitHub",
  tagline: "Network Inspector & API Studio",
  capabilityMatrix:
    "https://github.com/devuloopers/KNet#protocol-capability-matrix",
} as const;

/** Closed feature identities shared by the static panels and accessible local tab enhancement. */
export const featureIds = [
  "traffic",
  "api-studio",
  "intercepts",
  "network-conditions",
  "protected-traffic",
] as const;
export type FeatureId = (typeof featureIds)[number];

/** Initial feature when a visitor has not followed an explicit feature permalink. */
export const defaultFeatureId: FeatureId = "traffic";

/** Capability-backed short copy; protocol maturity remains explicit in the linked capability matrix. */
export const features = [
  {
    id: "traffic",
    label: "Traffic",
    title: "Every request. In context.",
    description:
      "Inspect requests, responses and timing. Find the details that explain your app’s behavior.",
    detail:
      "HTTP, GraphQL, WebSocket, gRPC and SSE inspection, with protocol-specific views.",
    docs: "https://github.com/devuloopers/KNet#desktop-traffic-inspection",
    alt: "KNet Traffic showing sample requests and a formatted JSON response beside separate device tabs.",
  },
  {
    id: "api-studio",
    label: "API Studio",
    title: "Replay. Edit. Verify.",
    description:
      "Send captured requests to API Studio. Adjust inputs and inspect the response.",
    detail:
      "Collections, authentication and Kotlin scripts keep your workflow together.",
    docs: "https://github.com/devuloopers/KNet#api-studio",
    alt: "Actual KNet API Studio collections, request parameters and a successful JSON response.",
  },
  {
    id: "intercepts",
    label: "Intercepts",
    title: "Pause at the right moment.",
    description:
      "Stop matching requests or responses. Edit the details before they continue.",
    detail: "Use protocol-aware breakpoint rules for focused debugging.",
    docs: "https://github.com/devuloopers/KNet#breakpoints-and-live-interception",
    alt: "KNet’s real interception drawer with a paused sample request and editing and forwarding controls.",
  },
  {
    id: "network-conditions",
    label: "Network Conditions",
    title: "Test beyond a perfect connection.",
    description:
      "Control upload, download, latency and jitter. Apply a profile or create precise rules.",
    detail: "Observe actual throughput for the selected device while testing.",
    docs: "https://github.com/devuloopers/KNet/blob/master/docs/network_conditions_implementation_plan.md",
    alt: "Actual Network Conditions workspace with the Streaming 100 kbps profile and a graph of authored sample throughput.",
  },
  {
    id: "protected-traffic",
    label: "Protected Traffic",
    title: "Choose how connections are handled.",
    description:
      "Inspect TLS traffic, tunnel encrypted connections or block destinations with saved rules.",
    detail:
      "Automatic fallback can learn explicit certificate rejection for later eligible connections.",
    docs: "https://github.com/devuloopers/KNet/blob/master/docs/automatic_tls_fallback.md",
    alt: "Actual Protected Traffic manager showing default TLS handling and sample inspect, tunnel and block rules.",
  },
] as const satisfies readonly {
  id: FeatureId;
  label: string;
  title: string;
  description: string;
  detail: string;
  docs: string;
  alt: string;
}[];

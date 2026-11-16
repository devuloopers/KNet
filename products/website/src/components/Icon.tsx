import type { SVGProps } from "react";

/** Small local symbols avoid a font, icon CDN or whole icon-library dependency. */
export function Icon({
  name,
  ...props
}: SVGProps<SVGSVGElement> & {
  name:
    | "github"
    | "devices"
    | "conditions"
    | "intercept"
    | "arrow"
    | "shield"
    | "copy"
    | "code"
    | "download";
}) {
  if (name === "github") {
    return (
      <svg
        viewBox="0 0 24 24"
        fill="currentColor"
        aria-hidden="true"
        {...props}
      >
        <path d="M12 .9a11.1 11.1 0 0 0-3.51 21.63c.55.1.76-.24.76-.53v-2.07c-3.09.67-3.74-1.31-3.74-1.31-.5-1.28-1.23-1.62-1.23-1.62-1.01-.69.08-.68.08-.68 1.11.08 1.7 1.14 1.7 1.14.99 1.69 2.6 1.2 3.23.92.1-.72.39-1.2.7-1.48-2.47-.28-5.07-1.24-5.07-5.49 0-1.21.43-2.2 1.14-2.98-.12-.28-.5-1.42.11-2.96 0 0 .94-.3 3.05 1.14a10.6 10.6 0 0 1 5.56 0c2.11-1.43 3.04-1.14 3.04-1.14.61 1.54.23 2.68.12 2.96.71.78 1.14 1.77 1.14 2.98 0 4.26-2.6 5.21-5.08 5.49.4.35.75 1.02.75 2.06V22c0 .29.2.64.77.53A11.1 11.1 0 0 0 12 .9Z" />
      </svg>
    );
  }
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      {name === "devices" && (
        <>
          <rect x="3" y="4" width="14" height="11" rx="1.5" />
          <path d="M7 20h6M10 15v5" />
          <rect x="17" y="10" width="5" height="10" rx="1" />
        </>
      )}
      {name === "conditions" && (
        <>
          <path d="M3 6h7m4 0h7M3 12h13m4 0h1M3 18h3m4 0h11" />
          <circle cx="12" cy="6" r="2" />
          <circle cx="18" cy="12" r="2" />
          <circle cx="8" cy="18" r="2" />
        </>
      )}
      {name === "intercept" && (
        <>
          <path d="M10 13a5 5 0 0 0 7 .1l3-3a5 5 0 0 0-7.1-7.1l-1.7 1.7M14 11a5 5 0 0 0-7-.1l-3 3a5 5 0 0 0 7.1 7.1l1.7-1.7" />
        </>
      )}
      {name === "shield" && (
        <path d="M12 3 3 7v6c0 5 9 9 9 9s9-4 9-9V7l-9-4Z" />
      )}
      {name === "copy" && (
        <>
          <rect x="8" y="8" width="13" height="13" rx="2" />
          <path d="M16 8V3H3v13h5" />
        </>
      )}
      {name === "code" && <path d="m8 6-6 6 6 6m8-12 6 6-6 6M14 3l-4 18" />}
      {name === "download" && <path d="M12 3v12m-5-5 5 5 5-5M3 16v5h18v-5" />}
      {name === "arrow" && <path d="M5 12h14m-5-5 5 5-5 5" />}
    </svg>
  );
}

import { site } from "../content/site";
import { Icon } from "./Icon";

/** Both prominent actions use one destination rather than browser-dependent binary selection. */
export function DownloadLink({ compact = false }: { compact?: boolean }) {
  return (
    <a
      className={`download-link${compact ? " download-link--compact" : ""}`}
      href={site.releases}
    >
      <Icon name="github" width="21" height="21" />
      <span>{site.downloadLabel}</span>
    </a>
  );
}

import { site } from "../content/site";

/** Uses the shared brand source copied by prepare-assets, without redrawing the KNet mark. */
export function Brand() {
  return (
    <a className="brand" href="/" aria-label={`${site.name} home`}>
      <img src="/brand/knet-icon.svg" alt="" width="40" height="40" />
      <span>{site.name}</span>
    </a>
  );
}

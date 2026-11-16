import { defaultFeatureId, featureIds, type FeatureId } from "./content/site";
import { createPageMotion } from "./motion";

/** Activates local responsive sources only when the related product panel is actually displayed. */
function revealImages(panel: HTMLElement) {
  for (const wrapper of panel.querySelectorAll<HTMLElement>(
    "[data-deferred-image]",
  )) {
    if (wrapper.hasAttribute("data-image-ready")) continue;
    for (const source of wrapper.querySelectorAll<HTMLSourceElement>(
      "source[data-srcset]",
    ))
      source.srcset = source.dataset.srcset!;
    const image = wrapper.querySelector<HTMLImageElement>("img[data-src]");
    if (image) {
      image.srcset = image.dataset.srcset!;
      image.src = image.dataset.src!;
    }
    wrapper.setAttribute("data-image-ready", "true");
  }
}

/** Enhances static feature links with manual-activation tab semantics, retaining a no-script document. */
function enhanceFeatureTour(
  root: ParentNode,
  motion: ReturnType<typeof createPageMotion>,
): () => void {
  const tour = root.querySelector<HTMLElement>("[data-feature-tour]");
  if (!tour || tour.hasAttribute("data-enhanced")) return () => {};
  const tabs = [
    ...tour.querySelectorAll<HTMLAnchorElement>("[data-feature-tab]"),
  ];
  const panels = [
    ...tour.querySelectorAll<HTMLElement>("[data-feature-panel]"),
  ];
  const tablist = tour.querySelector<HTMLElement>("[data-feature-tabs]")!;
  function fromHash(): FeatureId | undefined {
    const hash = location.hash.replace("#feature-", "");
    return featureIds.find((id) => id === hash);
  }
  let current: FeatureId | undefined;
  function select(id: FeatureId, updateAddress = false) {
    if (id === current) return;
    const previous = panels.find(
      (panel) => panel.dataset.featurePanel === current,
    );
    const next = panels.find((panel) => panel.dataset.featurePanel === id)!;
    current = id;
    motion.transitionFeature(previous, next, () => {
      tabs.forEach((tab) => {
        const selected = tab.dataset.featureTab === id;
        tab.setAttribute("aria-selected", String(selected));
        tab.tabIndex = selected ? 0 : -1;
      });
      panels.forEach((panel) => {
        panel.hidden = panel.dataset.featurePanel !== id;
        panel.inert = panel.hidden;
        panel.setAttribute("aria-hidden", String(panel.hidden));
        if (!panel.hidden) revealImages(panel);
      });
    });
    if (updateAddress) history.replaceState(null, "", `#feature-${id}`);
  }
  const controller = new AbortController();
  tablist.setAttribute("role", "tablist");
  tabs.forEach((tab, index) => {
    tab.setAttribute("role", "tab");
    tab.setAttribute("aria-controls", `feature-${tab.dataset.featureTab}`);
    tab.addEventListener(
      "click",
      (event) => {
        if (event.ctrlKey || event.metaKey || event.altKey || event.shiftKey)
          return;
        event.preventDefault();
        select(tab.dataset.featureTab as FeatureId, true);
      },
      { signal: controller.signal },
    );
    tab.addEventListener(
      "keydown",
      (event) => {
        let destination: number | undefined;
        if (event.key === "ArrowRight") destination = (index + 1) % tabs.length;
        if (event.key === "ArrowLeft")
          destination = (index + tabs.length - 1) % tabs.length;
        if (event.key === "Home") destination = 0;
        if (event.key === "End") destination = tabs.length - 1;
        if (destination !== undefined) {
          event.preventDefault();
          tabs.forEach((item, position) => {
            item.tabIndex = position === destination ? 0 : -1;
          });
          const next = tabs[destination];
          next?.focus();
          next?.scrollIntoView({ block: "nearest", inline: "nearest" });
        }
        if (event.key === " " || event.key === "Enter") {
          event.preventDefault();
          select(tab.dataset.featureTab as FeatureId, true);
        }
      },
      { signal: controller.signal },
    );
  });
  panels.forEach((panel) => {
    panel.setAttribute("role", "tabpanel");
    panel.setAttribute("aria-labelledby", `tab-${panel.dataset.featurePanel}`);
    panel.tabIndex = 0;
  });
  window.addEventListener(
    "hashchange",
    () => {
      const id = fromHash();
      if (id && id !== current) select(id);
    },
    { signal: controller.signal },
  );
  select(fromHash() ?? defaultFeatureId);
  tour.setAttribute("data-enhanced", "true");
  return () => {
    controller.abort();
    tour.removeAttribute("data-enhanced");
    tablist.removeAttribute("role");
    tabs.forEach((tab) => {
      tab.removeAttribute("role");
      tab.removeAttribute("aria-selected");
      tab.removeAttribute("aria-controls");
      tab.removeAttribute("tabindex");
    });
    panels.forEach((panel) => {
      panel.hidden = false;
      panel.inert = false;
      panel.removeAttribute("aria-hidden");
      panel.removeAttribute("role");
      panel.removeAttribute("tabindex");
      panel.setAttribute(
        "aria-labelledby",
        `heading-${panel.dataset.featurePanel}`,
      );
    });
  };
}

/** One progressive-enhancement lifetime owns tabs, motion, observers and cleanup. */
export function enhanceWebsite(
  root: Document | HTMLElement = document,
): () => void {
  const tour = root.querySelector<HTMLElement>("[data-feature-tour]");
  if (!tour || tour.hasAttribute("data-enhanced")) return () => {};
  const motion = createPageMotion(root);
  const disposeTour = enhanceFeatureTour(root, motion);
  return () => {
    motion.destroy();
    disposeTour();
  };
}

// Production is pre-rendered HTML; no React or Compose runtime is part of this enhancement.
if (typeof document !== "undefined") enhanceWebsite();

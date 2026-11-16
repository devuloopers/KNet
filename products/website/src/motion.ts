/** Browser-only motion ownership; static HTML stays visible when enhancement is unavailable. */
export function createPageMotion(root: Document | HTMLElement = document) {
  const documentRoot = root instanceof Document ? root : root.ownerDocument;
  const html = documentRoot.documentElement;
  const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
  const controller = new AbortController();
  const tabAnimations = new Map<HTMLElement, Animation>();
  const revealAnimations = new Map<HTMLElement, Animation>();
  const pending = new Set<HTMLElement>();
  const targets = [
    ...root.querySelectorAll<HTMLElement>(
      ".hero-copy, .product-preview, .feature-tour, .detail-card, .device-workspace, .companion-workspace, .utility, .closing",
    ),
  ];
  let observer: IntersectionObserver | undefined;

  /** Durations/easing come from the same theme tokens as CSS hover feedback. */
  function timing(token: string): KeyframeAnimationOptions {
    const style = getComputedStyle(html);
    return {
      duration: parseFloat(style.getPropertyValue(token)),
      easing: style.getPropertyValue("--motion-easing").trim(),
      fill: "both",
    };
  }

  /** Completed effects release their compositing resources and return to normal CSS. */
  function animate(
    element: HTMLElement,
    keyframes: Keyframe[],
    token: string,
    animations: Map<HTMLElement, Animation>,
    cleanup: () => void = () => {},
  ) {
    const animation = element.animate(keyframes, timing(token));
    animations.set(element, animation);
    animation.onfinish = () => {
      if (animations.get(element) !== animation) return;
      cleanup();
      animations.delete(element);
      animation.cancel();
    };
  }

  /** Cancelled transitions cannot leave a prior tab visible or intercepting input. */
  function cancelTabs() {
    for (const [element, animation] of tabAnimations) {
      element.removeAttribute("data-exiting");
      animation.onfinish = null;
      animation.cancel();
    }
    tabAnimations.clear();
  }

  /** Reveal immediately for focus, explicit anchors and reduced-motion changes. */
  function show(element: HTMLElement) {
    pending.delete(element);
    observer?.unobserve(element);
    element.removeAttribute("data-reveal-pending");
    element.setAttribute("data-revealed", "");
    revealAnimations.get(element)?.cancel();
    revealAnimations.delete(element);
  }

  /** Anchored and keyboard-focused content must never remain visually hidden. */
  function revealContaining(target: Node | null) {
    if (!target) return;
    for (const element of pending) if (element.contains(target)) show(element);
    for (const element of revealAnimations.keys())
      if (element.contains(target)) show(element);
  }

  function revealHash() {
    revealContaining(documentRoot.getElementById(location.hash.slice(1)));
  }

  /** Only offscreen targets are armed, avoiding a visible-then-hidden flash on initial load. */
  function setupReveals() {
    if (preference.matches || typeof IntersectionObserver === "undefined")
      return;
    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const element = entry.target as HTMLElement;
          if (!pending.has(element)) continue;
          show(element);
          if (typeof element.animate === "function")
            animate(
              element,
              [{ opacity: 0 }, { opacity: 1 }],
              "--motion-reveal",
              revealAnimations,
            );
        }
        if (!pending.size) observer?.disconnect();
      },
      { threshold: 0.06, rootMargin: "0px 0px -20px 0px" },
    );
    for (const element of targets) {
      if (element.getBoundingClientRect().top < window.innerHeight) continue;
      element.setAttribute("data-reveal-pending", "");
      pending.add(element);
      observer.observe(element);
    }
    revealHash();
  }

  function updatePreference() {
    html.toggleAttribute("data-motion-enabled", !preference.matches);
    if (!preference.matches) return;
    cancelTabs();
    for (const element of pending) show(element);
    observer?.disconnect();
    for (const animation of revealAnimations.values()) animation.cancel();
    revealAnimations.clear();
  }

  preference.addEventListener("change", updatePreference);
  root.addEventListener(
    "focusin",
    (event) => revealContaining(event.target as Node | null),
    { signal: controller.signal },
  );
  window.addEventListener("hashchange", revealHash, {
    signal: controller.signal,
  });
  updatePreference();
  setupReveals();

  return {
    /** Crossfade existing grid layers; accessibility and selection switch synchronously in activate. */
    transitionFeature(
      previous: HTMLElement | undefined,
      next: HTMLElement,
      activate: () => void,
    ) {
      const previousOpacity = previous
        ? getComputedStyle(previous).opacity
        : "1";
      const nextOpacity =
        !next.hidden || next.hasAttribute("data-exiting")
          ? getComputedStyle(next).opacity
          : "0";
      cancelTabs();
      activate();
      if (
        !previous ||
        previous === next ||
        preference.matches ||
        typeof next.animate !== "function"
      )
        return;
      previous.setAttribute("data-exiting", "");
      animate(
        previous,
        [{ opacity: previousOpacity }, { opacity: 0 }],
        "--motion-tab",
        tabAnimations,
        () => previous.removeAttribute("data-exiting"),
      );
      animate(
        next,
        [{ opacity: nextOpacity }, { opacity: 1 }],
        "--motion-tab",
        tabAnimations,
      );
    },
    /** Idempotent teardown supports development remounts without observers or animations accumulating. */
    destroy() {
      controller.abort();
      preference.removeEventListener("change", updatePreference);
      cancelTabs();
      observer?.disconnect();
      for (const animation of revealAnimations.values()) animation.cancel();
      revealAnimations.clear();
      pending.clear();
      for (const element of targets) {
        element.removeAttribute("data-reveal-pending");
        element.removeAttribute("data-revealed");
      }
      html.removeAttribute("data-motion-enabled");
    },
  };
}

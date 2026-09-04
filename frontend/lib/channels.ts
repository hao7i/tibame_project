import type { CSSProperties } from "react";

/**
 * 通路 tints from the design system: background, name colour, sub-label colour.
 *
 * These are the same three values the 收錄通路 strip, the 9x9 swatch in the offer
 * table and the watch list all use, so they live here rather than in one route.
 * Every value is a design token — no literal hex anywhere.
 */
type ChannelTint = {
  background: string;
  text: string;
  subText: string;
};

const CHANNEL_TINTS: Record<string, ChannelTint> = {
  BOOKS_TW: {
    background: "var(--color-accent-200)",
    text: "var(--color-accent-900)",
    subText: "var(--color-accent-800)",
  },
  SANMIN: {
    background: "var(--color-accent-300)",
    text: "var(--color-accent-900)",
    subText: "var(--color-accent-800)",
  },
  KINGSTONE: {
    background: "var(--color-accent-2-200)",
    text: "var(--color-accent-2-900)",
    subText: "var(--color-accent-2-800)",
  },
  TAAZE: {
    background: "var(--color-accent-2-400)",
    text: "var(--color-accent-2-900)",
    subText: "var(--color-accent-2-900)",
  },
  KOBO: {
    background: "var(--color-accent-700)",
    text: "var(--color-bg)",
    subText: "var(--color-accent-200)",
  },
  READMOO: {
    background: "var(--color-accent-800)",
    text: "var(--color-bg)",
    subText: "var(--color-accent-200)",
  },
};

const FALLBACK_TINT: ChannelTint = {
  background: "var(--color-neutral-300)",
  text: "var(--color-text)",
  subText: "var(--color-neutral-700)",
};

/**
 * Custom properties a tinted element reads, so the colours arrive as data while
 * the rules that use them stay in CSS.
 */
export function channelTintStyle(code: string): CSSProperties {
  const tint = CHANNEL_TINTS[code] ?? FALLBACK_TINT;
  return {
    "--tint-bg": tint.background,
    "--tint-text": tint.text,
    "--tint-sub": tint.subText,
  } as CSSProperties;
}

/** The swatch colour alone, for the small square beside a 通路 name. */
export function channelSwatch(code: string): string {
  return (CHANNEL_TINTS[code] ?? FALLBACK_TINT).background;
}

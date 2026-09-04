"use client";

import { useOptimistic, useState, useTransition } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { Facets } from "@/lib/api";
import { PRICE_CEILING } from "@/lib/filters";
import styles from "./search.module.css";

/**
 * 篩選條件 — the one interactive island on this screen.
 *
 * The rest of the results page is server-rendered and navigates with plain
 * links; this needs client JavaScript because the design gives the 價格上限 a
 * slider and no 套用 button, so filtering has to happen as the reader moves it.
 *
 * The URL remains the source of truth, but it only catches up once the server
 * has answered. Selections are therefore held optimistically in the meantime:
 * without that, a second tick during the round-trip would be computed from a
 * URL that still lacks the first one and would silently discard it, and the
 * checkbox would refuse to tick until the server replied.
 *
 * Every change drops the `page` parameter, which is how 「改條件時回到第一頁」
 * falls out rather than being special-cased.
 */
export function FacetRail({ facets }: { facets: Facets }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [, startTransition] = useTransition();
  const [channels, setChannels] = useOptimistic<string[]>(searchParams.getAll("channel"));

  const activeCeiling = searchParams.get("maxPrice");
  const ceiling = activeCeiling ? Number(activeCeiling) : PRICE_CEILING.max;

  const push = (params: URLSearchParams) => {
    params.delete("page");
    const queryString = params.toString();
    router.push(queryString ? `${pathname}?${queryString}` : pathname);
  };

  const toggleChannel = (value: string) => {
    // Built from the optimistic selection, not from the URL, so two quick
    // clicks compose instead of the second overwriting the first.
    const next = toggled(channels, value);

    const params = new URLSearchParams(searchParams.toString());
    params.delete("channel");
    for (const code of next) {
      params.append("channel", code);
    }

    startTransition(() => {
      setChannels(next);
      push(params);
    });
  };

  const commitCeiling = (value: number) => {
    // Releasing a key or the pointer without having moved the thumb must not
    // navigate: it would reset 分頁 and push a history entry for nothing.
    if (value === ceiling) {
      return;
    }

    const params = new URLSearchParams(searchParams.toString());
    if (value >= PRICE_CEILING.max) {
      params.delete("maxPrice");
    } else {
      params.set("maxPrice", String(value));
    }
    push(params);
  };

  const clearFilters = () => {
    const params = new URLSearchParams(searchParams.toString());
    // 搜尋 term is not a 篩選條件; it survives 清除篩選.
    for (const key of ["channel", "maxPrice", "page"]) {
      params.delete(key);
    }
    const queryString = params.toString();

    startTransition(() => {
      setChannels([]);
      router.push(queryString ? `${pathname}?${queryString}` : pathname);
    });
  };

  return (
    <aside className={styles.rail}>
      <h6 className={styles.railTitle}>篩選條件</h6>

      <FacetGroup
        name="通路"
        options={facets.channels.map((channel) => ({
          value: channel.code,
          label: channel.name,
          count: channel.count,
        }))}
        selected={channels}
        onToggle={toggleChannel}
      />

      {/* Keyed on the URL value so navigating (a chip, 清除篩選, the back
          button) remounts it at the new position, instead of syncing state to
          a prop from inside an effect. */}
      <PriceCeiling key={activeCeiling ?? "none"} initial={ceiling} onCommit={commitCeiling} />

      <button type="button" className="btn btn-secondary btn-block" onClick={clearFilters}>
        清除篩選
      </button>
    </aside>
  );
}

/**
 * 價格上限. The thumb position is local so the label tracks the drag, and the URL
 * is rewritten once, when the reader lets go — dragging otherwise fires a
 * navigation per pixel.
 */
function PriceCeiling({
  initial,
  onCommit,
}: {
  initial: number;
  onCommit: (value: number) => void;
}) {
  const [value, setValue] = useState(initial);

  return (
    <div className={styles.ceiling}>
      <label className={styles.ceilingLabel} htmlFor="price-ceiling">
        價格上限 NT$ {value}
      </label>
      <input
        id="price-ceiling"
        type="range"
        className={styles.range}
        min={PRICE_CEILING.min}
        max={PRICE_CEILING.max}
        step={PRICE_CEILING.step}
        value={value}
        onChange={(event) => setValue(Number(event.target.value))}
        onPointerUp={() => onCommit(value)}
        onKeyUp={() => onCommit(value)}
      />
    </div>
  );
}

function FacetGroup({
  name,
  options,
  selected,
  onToggle,
}: {
  name: string;
  options: { value: string; label: string; count: number }[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  return (
    <div className={styles.facetGroup}>
      <p className={styles.facetName}>{name}</p>
      {options.map((option) => (
        <label key={option.value} className={styles.facet}>
          <input
            type="checkbox"
            className={styles.checkbox}
            checked={selected.includes(option.value)}
            onChange={() => onToggle(option.value)}
          />
          <span className={styles.facetLabel}>{option.label}</span>
          <span className={styles.facetCount}>{option.count}</span>
        </label>
      ))}
    </div>
  );
}

function toggled(values: string[], value: string): string[] {
  return values.includes(value)
    ? values.filter((entry) => entry !== value)
    : [...values, value];
}

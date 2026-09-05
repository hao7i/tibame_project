"use client";

import { useOptimistic, useTransition } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { Facets } from "@/lib/api";
import styles from "./search.module.css";

/**
 * 篩選條件 — the one interactive island on this screen.
 *
 * The rest of the results page is server-rendered and navigates with plain
 * links; this needs client JavaScript so that a 通路 ticked while the previous
 * navigation is still in flight is not lost — see the optimistic state below.
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

      {/* 清除篩選 still drops maxPrice: the slider is gone, but a link someone
          saved while it existed can still carry one, and this is the only way
          left to get out of it. */}
      <button type="button" className="btn btn-secondary btn-block" onClick={clearFilters}>
        清除篩選
      </button>
    </aside>
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

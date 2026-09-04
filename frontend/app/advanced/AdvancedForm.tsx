"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { BlueprintCorners } from "@/components/Blueprint";
import type { Channel } from "@/lib/api";
import {
  BOOLEAN_OPS,
  EMPTY_ADVANCED,
  SEARCH_FIELDS,
  YEARS,
  advancedToParams,
  previewQuery,
  type AdvancedConditions,
} from "@/lib/advanced";
import styles from "./advanced.module.css";

/**
 * 進階搜尋 的表單.
 *
 * Held in component state rather than in the URL, unlike every other screen:
 * these conditions are being composed, not applied. Nothing is searched until
 * 執行搜尋, and only then do they become the URL of the 搜尋結果 page — which is
 * what makes that page's result the answer to exactly what 查詢式預覽 showed.
 */
export function AdvancedForm({ channels }: { channels: Channel[] }) {
  const router = useRouter();
  const [conditions, setConditions] = useState<AdvancedConditions>(EMPTY_ADVANCED);

  const update = (patch: Partial<AdvancedConditions>) => {
    setConditions((current) => ({ ...current, ...patch }));
  };

  const toggleChannel = (code: string) => {
    setConditions((current) => ({
      ...current,
      channels: current.channels.includes(code)
        ? current.channels.filter((entry) => entry !== code)
        : [...current.channels, code],
    }));
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const params = advancedToParams(conditions, channels);
    const queryString = params.toString();
    router.push(queryString ? `/search?${queryString}` : "/search");
  };

  return (
    <form className={styles.layout} onSubmit={submit}>
      <div>
        <h6 className={styles.kicker}>進階搜尋</h6>
        <h2 className={styles.heading}>組合欄位與價格條件</h2>

        <div className={styles.rows}>
          {/* Two fixed rows, no add-row control: the design draws exactly two,
              and 布林 only has meaning between them. */}
          <ConditionRow
            opLabel="條件"
            ops={["—"]}
            op="—"
            onOp={() => undefined}
            field={conditions.field1}
            term={conditions.term1}
            onField={(field1) => update({ field1 })}
            onTerm={(term1) => update({ term1 })}
            rowId="1"
          />
          <ConditionRow
            opLabel="布林"
            ops={BOOLEAN_OPS}
            op={conditions.op}
            onOp={(op) => update({ op: op as AdvancedConditions["op"] })}
            field={conditions.field2}
            term={conditions.term2}
            onField={(field2) => update({ field2 })}
            onTerm={(term2) => update({ term2 })}
            rowId="2"
          />
        </div>

        <div className={styles.resetRow}>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => setConditions(EMPTY_ADVANCED)}
          >
            重設
          </button>
        </div>

        <div className={`hr ${styles.divider}`} />

        <div className={styles.numbers}>
          <div className="field">
            <label htmlFor="price-from">價格下限 NT$</label>
            <input
              id="price-from"
              className="input"
              inputMode="numeric"
              placeholder="0"
              value={conditions.minPrice}
              onChange={(event) => update({ minPrice: event.target.value })}
            />
          </div>
          <div className="field">
            <label htmlFor="price-to">價格上限 NT$</label>
            <input
              id="price-to"
              className="input"
              inputMode="numeric"
              placeholder="500"
              value={conditions.maxPrice}
              onChange={(event) => update({ maxPrice: event.target.value })}
            />
          </div>
          <div className="field">
            <label htmlFor="year">出版年</label>
            <select
              id="year"
              className="input"
              value={conditions.year}
              onChange={(event) => update({ year: event.target.value })}
            >
              {YEARS.map((option) => (
                <option key={option.label} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className={styles.group}>
          <p className={styles.groupLabel}>限定通路</p>
          <div className={styles.channels}>
            {channels.map((channel) => (
              <label key={channel.code} className="radio">
                <input
                  type="checkbox"
                  className={styles.box}
                  checked={conditions.channels.includes(channel.code)}
                  onChange={() => toggleChannel(channel.code)}
                />
                <span className={styles.boxMark} aria-hidden="true" />
                {channel.name}
              </label>
            ))}
          </div>
        </div>


        <div className={styles.actions}>
          <button type="submit" className={`btn btn-primary blueprint ${styles.run}`}>
            執行搜尋
            <BlueprintCorners />
          </button>
          <Link href="/" className={`btn btn-secondary ${styles.run}`}>
            回簡易搜尋
          </Link>
        </div>
      </div>

      <aside>
        <div className="card blueprint">
          <span className="card-kicker">查詢式預覽</span>
          {/* Live, and the same string the URL is built from — so what the
              reader is promised here is what the results page answers. */}
          <p className={styles.preview}>{previewQuery(conditions, channels)}</p>
          <div className="card-meta">系統會依此條件向各通路取價</div>
          <BlueprintCorners />
        </div>

        <div className={`card blueprint ${styles.note}`}>
          <span className="card-kicker">說明</span>
          <p className="card-body">
            以 AND／OR／NOT 串接欄位條件；價格區間比對的是各通路目前售價，未勾選通路表示全部收錄通路。
          </p>
          <BlueprintCorners />
        </div>
      </aside>
    </form>
  );
}

function ConditionRow({
  opLabel,
  ops,
  op,
  onOp,
  field,
  term,
  onField,
  onTerm,
  rowId,
}: {
  opLabel: string;
  ops: readonly string[];
  op: string;
  onOp: (value: string) => void;
  field: string;
  term: string;
  onField: (value: string) => void;
  onTerm: (value: string) => void;
  rowId: string;
}) {
  return (
    <div className={styles.row}>
      <div className="field">
        <label htmlFor={`op-${rowId}`}>{opLabel}</label>
        <select
          id={`op-${rowId}`}
          className="input"
          value={op}
          disabled={ops.length === 1}
          onChange={(event) => onOp(event.target.value)}
        >
          {ops.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor={`field-${rowId}`}>搜尋欄位</label>
        <select
          id={`field-${rowId}`}
          className="input"
          value={field}
          onChange={(event) => onField(event.target.value)}
        >
          {SEARCH_FIELDS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor={`term-${rowId}`}>關鍵字</label>
        <input
          id={`term-${rowId}`}
          className="input"
          placeholder="輸入關鍵字"
          value={term}
          onChange={(event) => onTerm(event.target.value)}
        />
      </div>
    </div>
  );
}

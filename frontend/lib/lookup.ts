"use server";

import { revalidatePath } from "next/cache";
import { BACKEND_URL } from "@/lib/api";

export type LookupOutcome =
  | { status: "imported"; count: number }
  | { status: "none" }
  | { status: "error"; message: string };

/**
 * 拿書名去通路找書，找到就收進書目並取價.
 *
 * Slow by nature: a 搜尋 at 金石堂, a 商品頁 per candidate, then 取價 at all five
 * 通路. That is why it is a 明確的動作 behind a button rather than something the
 * 搜尋 does by itself — a reader who typed a typo should not cost five shops a
 * page fetch each.
 */
export async function lookupByTitle(title: string): Promise<LookupOutcome> {
  const term = title.trim();
  if (!term) {
    return { status: "none" };
  }

  try {
    const response = await fetch(`${BACKEND_URL}/api/lookups`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: term }),
      cache: "no-store",
    });

    if (!response.ok) {
      return { status: "error", message: "找書失敗，請稍後再試一次。" };
    }

    const body = (await response.json()) as { imported: string[] };
    if (body.imported.length === 0) {
      return { status: "none" };
    }

    // The 搜尋結果 the reader is looking at was rendered before these existed.
    revalidatePath("/search");
    return { status: "imported", count: body.imported.length };
  } catch {
    return { status: "error", message: "找書失敗，請稍後再試一次。" };
  }
}

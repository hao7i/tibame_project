"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { BACKEND_URL } from "@/lib/api";
import { currentMember, sessionHeader } from "@/lib/session";

/**
 * 追蹤 actions.
 *
 * Every one of these re-checks the session for itself. Rendering the button
 * only for a signed-in reader is not a security boundary — a Server Action can
 * be invoked without going through the UI at all — so the check has to be here,
 * and the backend requires ROLE_MEMBER underneath it besides.
 */

async function send(path: string, method: string, body?: unknown): Promise<boolean> {
  const response = await fetch(`${BACKEND_URL}/api/me/watchlist${path}`, {
    method,
    headers: {
      ...(await sessionHeader()),
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
  return response.ok;
}

/**
 * 追蹤 one 作品, or send an anonymous reader to 登入 first.
 *
 * The 書名 and the ISBN travel with the detour so that 登入 can say which book is
 * waiting, and add it and come back here once the reader is in — which is what
 * 「登入後那本書自動進入清單」 asks for.
 */
export async function addToWatchList(isbn: string, title: string): Promise<void> {
  if (!(await currentMember())) {
    redirect(`/login?book=${encodeURIComponent(title)}&isbn=${encodeURIComponent(isbn)}`);
  }

  await send(`/${encodeURIComponent(isbn)}`, "PUT");
  refreshWatchViews(isbn);
}

export async function removeFromWatchList(isbn: string): Promise<void> {
  if (!(await currentMember())) {
    redirect("/login");
  }

  await send(`/${encodeURIComponent(isbn)}`, "DELETE");
  refreshWatchViews(isbn);
}

export async function clearWatchList(): Promise<void> {
  if (!(await currentMember())) {
    redirect("/login");
  }

  await send("", "DELETE");
  refreshWatchViews();
}

/**
 * 設定目標價 from the inline field on 追蹤清單.
 *
 * A blank field clears the 目標價 rather than failing: erasing the number is how
 * a reader says they no longer have one in mind.
 */
export async function setTargetPrice(isbn: string, form: FormData): Promise<void> {
  if (!(await currentMember())) {
    redirect("/login");
  }

  const raw = String(form.get("targetPrice") ?? "").trim();
  const targetPrice = raw === "" ? null : Number(raw);

  if (targetPrice !== null && (!Number.isInteger(targetPrice) || targetPrice < 1)) {
    // Refused rather than sent on: the backend would reject it anyway, and the
    // row should keep the number it already had.
    return;
  }

  await send(`/${encodeURIComponent(isbn)}/target-price`, "PUT", { targetPrice });
  refreshWatchViews(isbn);
}

/**
 * The 導覽列 count sits in the root layout, so every screen showing it has to be
 * re-rendered after the list changes — not just the one the reader pressed.
 */
function refreshWatchViews(isbn?: string): void {
  revalidatePath("/watch");
  revalidatePath("/search");
  if (isbn) {
    revalidatePath(`/works/${isbn}`);
  }
}

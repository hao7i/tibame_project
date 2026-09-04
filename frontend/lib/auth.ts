"use server";

import { redirect } from "next/navigation";
import { BACKEND_URL } from "@/lib/api";
import { clearSession, sessionHeader, writeSession } from "@/lib/session";

/**
 * 註冊, 登入 and 登出, as Server Actions.
 *
 * They run on the Next.js server so that the session Spring issues is captured
 * here and never exposed to the browser (see lib/session.ts). Being actions
 * rather than client fetches also means 登入 works from a plain form submit.
 */

export type AuthResult = { error: string } | undefined;

export async function signIn(_previous: AuthResult, form: FormData): Promise<AuthResult> {
  const email = String(form.get("email") ?? "");
  const password = String(form.get("password") ?? "");

  if (!email || !password) {
    return { error: "請輸入電子郵件與密碼" };
  }

  // Spring Security form login expects form encoding, not JSON.
  const response = await fetch(`${BACKEND_URL}/api/session`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ email, password }),
    redirect: "manual",
    cache: "no-store",
  });

  if (!response.ok) {
    return { error: "帳號或密碼不正確" };
  }

  const session = await writeSession(response.headers.get("set-cookie"));
  if (!session) {
    // A 200 with no session would leave the reader looking signed in on this
    // page and anonymous on the next one.
    return { error: "登入失敗，請再試一次" };
  }

  // The 追蹤 detour: the reader pressed 追蹤 while signed out and was sent here.
  // Add the book now and land them back on it, so the press they made before
  // the interruption is the press that takes effect.
  const pendingIsbn = String(form.get("pendingIsbn") ?? "").trim();
  if (pendingIsbn) {
    await fetch(`${BACKEND_URL}/api/me/watchlist/${encodeURIComponent(pendingIsbn)}`, {
      method: "PUT",
      // The session just issued, rather than the cookie written moments ago:
      // one less thing that has to be true for the detour to work.
      headers: { Cookie: `JSESSIONID=${session}` },
      cache: "no-store",
    }).catch(() => undefined);

    redirect(`/works/${encodeURIComponent(pendingIsbn)}`);
  }

  redirect("/");
}

export async function register(_previous: AuthResult, form: FormData): Promise<AuthResult> {
  const email = String(form.get("email") ?? "");
  const password = String(form.get("password") ?? "");

  if (password.length < 8) {
    return { error: "密碼至少 8 個字元" };
  }

  const response = await fetch(`${BACKEND_URL}/api/members`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
    cache: "no-store",
  });

  if (response.status === 409) {
    return { error: "這個電子郵件已經註冊過了" };
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    return { error: body?.error?.message ?? "註冊失敗，請確認輸入內容" };
  }

  // 註冊 signs the reader straight in: making them retype what they just chose
  // would be a step with no purpose.
  return signIn(undefined, form);
}

export async function signOut(): Promise<void> {
  await fetch(`${BACKEND_URL}/api/session/logout`, {
    method: "POST",
    headers: await sessionHeader(),
    cache: "no-store",
  }).catch(() => undefined);

  // Cleared even if the backend call failed: the reader asked to be signed out,
  // and the session this server holds is what keeps them signed in.
  await clearSession();
  redirect("/");
}

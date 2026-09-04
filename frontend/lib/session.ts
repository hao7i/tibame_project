import { cookies } from "next/headers";
import { BACKEND_URL, type Member } from "@/lib/api";

/**
 * 會員 session, carried between the browser and Spring.
 *
 * The browser never talks to the backend: every call is made by the Next.js
 * server, which is what keeps the backend origin private. That has one
 * consequence for 登入 — the JSESSIONID Spring issues is handed to *this*
 * server, not to the browser, so this module is what remembers it.
 *
 * It is kept in an httpOnly cookie on this origin and attached, server-side, to
 * the calls that need it. The browser therefore holds a session identifier it
 * cannot read, and no page on another site can make it reach the backend.
 */
const SESSION_COOKIE = "bookprice_session";

/** Spring names its session cookie this; we forward only that one. */
const BACKEND_COOKIE = "JSESSIONID";

/** The session Spring issued, or undefined when nobody is signed in. */
export async function readSession(): Promise<string | undefined> {
  const store = await cookies();
  return store.get(SESSION_COOKIE)?.value;
}

/**
 * The Cookie header for a backend call, empty when there is no session.
 *
 * Public 書目 calls work either way; sending it when we have it is what makes
 * 會員專屬 endpoints answer as the signed-in reader.
 */
export async function sessionHeader(): Promise<Record<string, string>> {
  const session = await readSession();
  return session ? { Cookie: `${BACKEND_COOKIE}=${session}` } : {};
}

/**
 * Stores the session from a backend Set-Cookie header.
 *
 * `sameSite: lax` and `httpOnly` mirror what the backend would have set had the
 * browser spoken to it directly; `secure` is off because local development is
 * plain HTTP, and a cookie marked secure would simply never be stored.
 */
export async function writeSession(setCookieHeader: string | null): Promise<boolean> {
  const value = parseJsessionId(setCookieHeader);
  if (!value) {
    return false;
  }

  const store = await cookies();
  store.set(SESSION_COOKIE, value, {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    secure: process.env.NODE_ENV === "production",
  });
  return true;
}

export async function clearSession(): Promise<void> {
  const store = await cookies();
  store.delete(SESSION_COOKIE);
}

/**
 * Pulls JSESSIONID out of a Set-Cookie header.
 *
 * A logout response also carries a Set-Cookie for JSESSIONID — an expiry with
 * an empty value — so an empty match is treated as no session rather than as a
 * session whose identifier happens to be blank.
 */
function parseJsessionId(setCookieHeader: string | null): string | undefined {
  if (!setCookieHeader) {
    return undefined;
  }
  const match = setCookieHeader.match(/JSESSIONID=([^;,\s]+)/);
  return match?.[1] || undefined;
}

/**
 * The signed-in 會員, or null when nobody is.
 *
 * Deliberately here rather than beside the 登入 actions: everything exported
 * from a "use server" module becomes an endpoint the browser can call, and a
 * read this ordinary has no business being one.
 *
 * Never throws — 未登入 is an answer, not a fault, and the 導覽列 on every page
 * depends on getting one.
 */
export async function currentMember(): Promise<Member | null> {
  const response = await fetch(`${BACKEND_URL}/api/me`, {
    headers: { Accept: "application/json", ...(await sessionHeader()) },
    cache: "no-store",
  });

  if (!response.ok) {
    return null;
  }
  return (await response.json()) as Member;
}

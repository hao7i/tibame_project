import { NextResponse } from "next/server";
import { BACKEND_URL } from "@/lib/api";

/**
 * 找書 as an ordinary HTTP endpoint rather than a Server Action.
 *
 * That distinction is the whole point of this file. A Server Action shares a
 * queue with router navigation, so while one was in flight — and this one runs
 * for over a minute — every link on the page did nothing when clicked. A plain
 * route handler is outside that queue: the browser fires a normal request, the
 * reader carries on, and the answer arrives whenever it arrives.
 *
 * It also keeps the browser talking only to Next, never to Spring directly,
 * which is how every other call on this site is arranged.
 */
export async function POST(request: Request) {
  let term: unknown;
  try {
    ({ term } = await request.json());
  } catch {
    return NextResponse.json({ error: "請提供書名或 ISBN" }, { status: 400 });
  }

  if (typeof term !== "string" || !term.trim()) {
    return NextResponse.json({ error: "請提供書名或 ISBN" }, { status: 400 });
  }

  const response = await fetch(`${BACKEND_URL}/api/lookups`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ term: term.trim() }),
    cache: "no-store",
  });

  if (!response.ok) {
    return NextResponse.json({ error: "找書失敗" }, { status: 502 });
  }

  const body = (await response.json()) as { imported: string[] };
  return NextResponse.json(body);
}

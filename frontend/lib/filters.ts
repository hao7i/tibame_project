/**
 * 篩選條件 constants shared by the server-rendered results page and the client
 * facet rail.
 *
 * It lives here, not in FacetRail, because a "use client" module reaches a
 * Server Component as a client-reference proxy: importing this from there gave
 * an undefined max, which silently dropped the 價格上限 chip instead of failing.
 */
export const PRICE_CEILING = { min: 150, max: 700, step: 10 };
